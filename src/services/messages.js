const { pool, query } = require('../db');
const { HttpError } = require('../utils/errors');
const { requireMember } = require('./chats');
const { pushToUsers } = require('./push');

// $1 = id pengguna yang melihat (viewer)
const SELECT = `
SELECT m.id, m.chat_id, m.sender_id, m.type,
  CASE WHEN m.deleted_at IS NULL THEN m.body END AS body,
  CASE WHEN m.deleted_at IS NULL THEN m.media_id END AS media_id,
  CASE WHEN m.deleted_at IS NULL THEN md.mime END AS media_mime,
  CASE WHEN m.deleted_at IS NULL THEN md.filename END AS media_filename,
  CASE WHEN m.deleted_at IS NULL THEN md.size END AS media_size,
  m.created_at, m.edited_at, (m.deleted_at IS NOT NULL) AS deleted,
  CASE WHEN m.reply_to IS NOT NULL THEN json_build_object(
    'id', rm.id, 'sender_id', rm.sender_id, 'type', rm.type,
    'body', CASE WHEN rm.deleted_at IS NULL THEN left(rm.body, 120) END,
    'deleted', (rm.deleted_at IS NOT NULL)) END AS reply,
  CASE WHEN m.sender_id = $1 THEN (
    SELECT CASE
      WHEN COUNT(*) = 0 THEN 'sent'
      WHEN COUNT(*) FILTER (WHERE r.read_at IS NULL) = 0 THEN 'read'
      WHEN COUNT(*) FILTER (WHERE r.delivered_at IS NULL) = 0 THEN 'delivered'
      ELSE 'sent' END
    FROM message_receipts r WHERE r.message_id = m.id) END AS status,
  COALESCE((SELECT json_agg(json_build_object('user_id', x.user_id, 'emoji', x.emoji))
            FROM message_reactions x WHERE x.message_id = m.id), '[]'::json) AS reactions
FROM messages m
LEFT JOIN media md ON md.id = m.media_id
LEFT JOIN messages rm ON rm.id = m.reply_to
`;

async function getMessage(id, viewerId) {
  const { rows } = await query(`${SELECT} WHERE m.id = $2`, [viewerId, id]);
  if (!rows[0]) throw new HttpError(404, 'Pesan tidak ditemukan');
  return rows[0];
}

async function listMessages(chatId, viewerId, { beforeId, limit }) {
  const { rows } = await query(
    `${SELECT}
     WHERE m.chat_id = $2
       AND ($3::uuid IS NULL OR m.created_at < (SELECT created_at FROM messages WHERE id = $3::uuid))
     ORDER BY m.created_at DESC LIMIT $4`,
    [viewerId, chatId, beforeId || null, limit]
  );
  return rows;
}

const typeFromMime = (mime) =>
  mime.startsWith('image/') ? 'image' : mime.startsWith('video/') ? 'video' : mime.startsWith('audio/') ? 'audio' : 'file';

async function isOnline(io, userId) {
  return (await io.in(`user:${userId}`).fetchSockets()).length > 0;
}

// Status teragregasi (sent/delivered/read) untuk daftar pesan
async function aggregateStatuses(ids) {
  if (!ids.length) return {};
  const { rows } = await query(
    `SELECT message_id,
       CASE WHEN COUNT(*) FILTER (WHERE read_at IS NULL) = 0 THEN 'read'
            WHEN COUNT(*) FILTER (WHERE delivered_at IS NULL) = 0 THEN 'delivered'
            ELSE 'sent' END AS status
     FROM message_receipts WHERE message_id = ANY($1::uuid[]) GROUP BY message_id`,
    [ids]
  );
  return Object.fromEntries(rows.map((r) => [r.message_id, r.status]));
}

// rows: [{message_id, chat_id, sender_id}] -> beri tahu pengirim
async function emitStatuses(io, rows) {
  if (!rows.length) return;
  const map = await aggregateStatuses([...new Set(rows.map((r) => r.message_id))]);
  for (const r of rows) {
    io.to(`user:${r.sender_id}`).emit('message:status', {
      chat_id: r.chat_id,
      message_id: r.message_id,
      status: map[r.message_id] || 'sent',
    });
  }
}

async function sendMessage(io, { chatId, senderId, body, mediaId, replyTo, clientId }) {
  await requireMember(chatId, senderId);

  body = typeof body === 'string' ? body.trim() : '';
  if (body.length > 4096) throw new HttpError(400, 'Pesan terlalu panjang (maks 4096 karakter)');

  let type = 'text';
  if (mediaId) {
    const { rows } = await query('SELECT mime, owner_id FROM media WHERE id = $1', [mediaId]);
    if (!rows[0] || rows[0].owner_id !== senderId) throw new HttpError(400, 'Media tidak ditemukan');
    type = typeFromMime(rows[0].mime);
  } else if (!body) {
    throw new HttpError(400, 'Isi pesan tidak boleh kosong');
  }

  if (replyTo) {
    const r = await query('SELECT 1 FROM messages WHERE id = $1 AND chat_id = $2', [replyTo, chatId]);
    if (!r.rows[0]) throw new HttpError(400, 'Pesan yang dibalas tidak ditemukan di chat ini');
  }

  const client = await pool.connect();
  let messageId;
  let recipients;
  try {
    await client.query('BEGIN');
    const ins = await client.query(
      `INSERT INTO messages (chat_id, sender_id, type, body, media_id, reply_to)
       VALUES ($1,$2,$3,$4,$5,$6) RETURNING id`,
      [chatId, senderId, type, body || null, mediaId || null, replyTo || null]
    );
    messageId = ins.rows[0].id;
    const mem = await client.query('SELECT user_id FROM chat_members WHERE chat_id = $1 AND user_id <> $2', [chatId, senderId]);
    recipients = mem.rows.map((r) => r.user_id);
    if (recipients.length) {
      await client.query(
        'INSERT INTO message_receipts (message_id, user_id) SELECT $1, unnest($2::uuid[])',
        [messageId, recipients]
      );
    }
    await client.query('COMMIT');
  } catch (e) {
    await client.query('ROLLBACK').catch(() => {});
    throw e;
  } finally {
    client.release();
  }

  // Tandai delivered bagi penerima yang sedang online
  const online = [];
  const offline = [];
  for (const uid of recipients) ((await isOnline(io, uid)) ? online : offline).push(uid);
  if (online.length) {
    await query(
      'UPDATE message_receipts SET delivered_at = now() WHERE message_id = $1 AND user_id = ANY($2::uuid[])',
      [messageId, online]
    );
  }

  const message = await getMessage(messageId, senderId);
  io.to(`chat:${chatId}`).emit('message:new', { ...message, client_id: clientId || null });

  // Push notification untuk penerima offline yang tidak mute
  if (offline.length) {
    (async () => {
      const m = await query(
        'SELECT user_id FROM chat_members WHERE chat_id = $1 AND user_id = ANY($2::uuid[]) AND muted = false',
        [chatId, offline]
      );
      const u = await query('SELECT display_name FROM users WHERE id = $1', [senderId]);
      const preview = type === 'text' ? body.slice(0, 100)
        : { image: '📷 Foto', video: '🎥 Video', audio: '🎤 Audio', file: '📎 Dokumen' }[type];
      await pushToUsers(m.rows.map((r) => r.user_id), {
        title: u.rows[0]?.display_name || 'Pesan baru',
        body: preview,
        data: { chat_id: chatId, message_id: messageId },
      });
    })().catch((e) => console.error('[push]', e.message));
  }

  return message;
}

// Tandai pesan masuk sebagai delivered saat pengguna online
async function markDeliveredForUser(io, userId) {
  const { rows } = await query(
    `UPDATE message_receipts r SET delivered_at = now()
     FROM messages m
     WHERE r.message_id = m.id AND r.user_id = $1 AND r.delivered_at IS NULL
     RETURNING r.message_id, m.chat_id, m.sender_id`,
    [userId]
  );
  await emitStatuses(io, rows);
}

async function markRead(io, chatId, userId) {
  await requireMember(chatId, userId);
  const { rows } = await query(
    `UPDATE message_receipts r SET read_at = now(), delivered_at = COALESCE(r.delivered_at, now())
     FROM messages m
     WHERE r.message_id = m.id AND m.chat_id = $1 AND r.user_id = $2 AND r.read_at IS NULL
     RETURNING r.message_id, m.chat_id, m.sender_id`,
    [chatId, userId]
  );
  await query('UPDATE chat_members SET last_read_at = now() WHERE chat_id = $1 AND user_id = $2', [chatId, userId]);
  await emitStatuses(io, rows);
  io.to(`user:${userId}`).emit('chat:read', { chat_id: chatId, user_id: userId });
  return rows.length;
}

module.exports = {
  getMessage, listMessages, sendMessage, markRead, markDeliveredForUser, isOnline, SELECT,
};
