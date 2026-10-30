const router = require('express').Router();
const { pool, query } = require('../db');
const { HttpError, wrap } = require('../utils/errors');
const { requireMember, listChats, getChat } = require('../services/chats');
const { sendMessage, listMessages, markRead } = require('../services/messages');

const uniq = (a) => [...new Set(a)];

// Pastikan semua socket anggota bergabung ke room chat & kabari chat baru
async function announceChat(io, chatId, userIds) {
  for (const uid of userIds) {
    io.in(`user:${uid}`).socketsJoin(`chat:${chatId}`);
    io.to(`user:${uid}`).emit('chat:new', await getChat(chatId, uid));
  }
}

router.get('/', wrap(async (req, res) => {
  res.json(await listChats(req.userId));
}));

// Buat / ambil chat 1-1
router.post('/direct', wrap(async (req, res) => {
  const peerId = String(req.body.user_id || '');
  if (!peerId || peerId === req.userId) throw new HttpError(400, 'user_id tidak valid');
  const u = await query('SELECT 1 FROM users WHERE id = $1', [peerId]);
  if (!u.rows[0]) throw new HttpError(404, 'Pengguna tidak ditemukan');

  const key = [req.userId, peerId].sort().join(':');
  const client = await pool.connect();
  let chatId, created = false;
  try {
    await client.query('BEGIN');
    const ins = await client.query(
      `INSERT INTO chats (type, direct_key, created_by) VALUES ('direct', $1, $2)
       ON CONFLICT (direct_key) DO NOTHING RETURNING id`, [key, req.userId]);
    if (ins.rows[0]) {
      created = true;
      chatId = ins.rows[0].id;
      await client.query(
        'INSERT INTO chat_members (chat_id, user_id) VALUES ($1,$2),($1,$3)', [chatId, req.userId, peerId]);
    } else {
      chatId = (await client.query('SELECT id FROM chats WHERE direct_key = $1', [key])).rows[0].id;
    }
    await client.query('COMMIT');
  } catch (e) {
    await client.query('ROLLBACK').catch(() => {});
    throw e;
  } finally {
    client.release();
  }
  const io = req.app.get('io');
  if (created) await announceChat(io, chatId, [req.userId, peerId]);
  res.status(created ? 201 : 200).json(await getChat(chatId, req.userId));
}));

// Buat grup
router.post('/group', wrap(async (req, res) => {
  const name = String(req.body.name || '').trim();
  const description = req.body.description ? String(req.body.description).slice(0, 500) : null;
  const isPublic = req.body.is_public === true;
  const publicHandle = isPublic ? String(req.body.public_handle || '').trim().toLowerCase() : null;
  const communityName = isPublic ? String(req.body.community_name || '').trim().slice(0, 80) || name : null;
  const memberIds = uniq((req.body.member_ids || []).filter((id) => id !== req.userId));
  if (!name || name.length > 60) throw new HttpError(400, 'Nama grup 1-60 karakter');
  if (isPublic && !/^[a-z0-9_]{3,40}$/.test(publicHandle || '')) throw new HttpError(400, 'Handle publik 3-40 karakter');
  if (!memberIds.length && !isPublic) throw new HttpError(400, 'Minimal 1 anggota selain Anda');
  if (memberIds.length > 255) throw new HttpError(400, 'Maksimal 256 anggota');
  const found = await query('SELECT id FROM users WHERE id = ANY($1::uuid[])', [memberIds]);
  if (found.rows.length !== memberIds.length) throw new HttpError(400, 'Ada anggota yang tidak ditemukan');

  const client = await pool.connect();
  let chatId;
  try {
    await client.query('BEGIN');
    const c = await client.query(
      `INSERT INTO chats (type, name, description, created_by, is_public, public_handle, community_name)
       VALUES ('group',$1,$2,$3,$4,$5,$6) RETURNING id`,
      [name, description, req.userId, isPublic, publicHandle, communityName]);
    chatId = c.rows[0].id;
    await client.query("INSERT INTO chat_members (chat_id, user_id, role) VALUES ($1,$2,'admin')", [chatId, req.userId]);
    await client.query('INSERT INTO chat_members (chat_id, user_id) SELECT $1, unnest($2::uuid[])', [chatId, memberIds]);
    await client.query('COMMIT');
  } catch (e) {
    await client.query('ROLLBACK').catch(() => {});
    throw e;
  } finally {
    client.release();
  }
  await announceChat(req.app.get('io'), chatId, [req.userId, ...memberIds]);
  res.status(201).json(await getChat(chatId, req.userId));
}));

router.get('/:id', wrap(async (req, res) => {
  res.json(await getChat(req.params.id, req.userId));
}));

// Ubah info grup (admin)
router.patch('/:id', wrap(async (req, res) => {
  const me = await requireMember(req.params.id, req.userId);
  const chat = await query('SELECT type FROM chats WHERE id = $1', [req.params.id]);
  if (chat.rows[0].type !== 'group') throw new HttpError(400, 'Hanya untuk grup');
  if (me.role !== 'admin') throw new HttpError(403, 'Hanya admin');
  const name = req.body.name !== undefined ? String(req.body.name).trim() : null;
  if (name !== null && (!name || name.length > 60)) throw new HttpError(400, 'Nama grup 1-60 karakter');
  const ttl = req.body.message_ttl_seconds === undefined ? null : Number(req.body.message_ttl_seconds);
  if (ttl !== null && ![0, 86400, 604800, 7776000].includes(ttl)) {
    throw new HttpError(400, 'Durasi pesan sementara tidak valid');
  }
  const isPublic = req.body.is_public === undefined ? null : req.body.is_public === true;
  const publicHandle = isPublic === true
    ? String(req.body.public_handle || '').trim().toLowerCase()
    : isPublic === false ? null : null;
  if (isPublic === true && !/^[a-z0-9_]{3,40}$/.test(publicHandle)) {
    throw new HttpError(400, 'Handle publik 3-40 karakter');
  }
  await query(
    `UPDATE chats SET name = COALESCE($2, name), description = COALESCE($3, description),
      message_ttl_seconds = COALESCE($4, message_ttl_seconds),
      is_public = COALESCE($5, is_public), public_handle = CASE WHEN $5 = false THEN NULL ELSE COALESCE($6, public_handle) END,
      community_name = CASE WHEN $5 = false THEN NULL ELSE COALESCE($7, community_name) END WHERE id = $1`,
    [req.params.id, name, req.body.description !== undefined ? String(req.body.description).slice(0, 500) : null, ttl,
      isPublic, publicHandle, req.body.community_name ? String(req.body.community_name).slice(0, 80) : null]);
  const io = req.app.get('io');
  io.to(`chat:${req.params.id}`).emit('chat:updated', { chat_id: req.params.id });
  res.json(await getChat(req.params.id, req.userId));
}));

// Bisukan / aktifkan notifikasi chat
router.patch('/:id/mute', wrap(async (req, res) => {
  await requireMember(req.params.id, req.userId);
  await query('UPDATE chat_members SET muted = $3 WHERE chat_id = $1 AND user_id = $2',
    [req.params.id, req.userId, !!req.body.muted]);
  res.json({ ok: true, muted: !!req.body.muted });
}));

// Tambah anggota (admin)
router.post('/:id/members', wrap(async (req, res) => {
  const me = await requireMember(req.params.id, req.userId);
  const chat = await query('SELECT type FROM chats WHERE id = $1', [req.params.id]);
  if (chat.rows[0].type !== 'group') throw new HttpError(400, 'Hanya untuk grup');
  if (me.role !== 'admin') throw new HttpError(403, 'Hanya admin');
  const ids = uniq(req.body.user_ids || []);
  if (!ids.length) throw new HttpError(400, 'user_ids wajib');
  const ins = await query(
    `INSERT INTO chat_members (chat_id, user_id)
     SELECT $1, u.id FROM users u WHERE u.id = ANY($2::uuid[])
     ON CONFLICT DO NOTHING RETURNING user_id`, [req.params.id, ids]);
  const io = req.app.get('io');
  const added = ins.rows.map((r) => r.user_id);
  await announceChat(io, req.params.id, added);
  io.to(`chat:${req.params.id}`).emit('chat:updated', { chat_id: req.params.id });
  res.json(await getChat(req.params.id, req.userId));
}));

// Keluarkan anggota (admin) atau keluar sendiri
router.delete('/:id/members/:userId', wrap(async (req, res) => {
  const me = await requireMember(req.params.id, req.userId);
  const chat = await query('SELECT type FROM chats WHERE id = $1', [req.params.id]);
  if (chat.rows[0].type !== 'group') throw new HttpError(400, 'Hanya untuk grup');
  const self = req.params.userId === req.userId;
  if (!self && me.role !== 'admin') throw new HttpError(403, 'Hanya admin');

  const del = await query('DELETE FROM chat_members WHERE chat_id = $1 AND user_id = $2 RETURNING user_id',
    [req.params.id, req.params.userId]);
  if (!del.rows[0]) throw new HttpError(404, 'Anggota tidak ditemukan');

  const io = req.app.get('io');
  io.to(`chat:${req.params.id}`).emit('chat:member_removed', { chat_id: req.params.id, user_id: req.params.userId });
  io.in(`user:${req.params.userId}`).socketsLeave(`chat:${req.params.id}`);

  // Pastikan masih ada admin; jika grup kosong, hapus
  const left = await query('SELECT user_id, role FROM chat_members WHERE chat_id = $1 ORDER BY joined_at', [req.params.id]);
  if (!left.rows.length) {
    await query('DELETE FROM chats WHERE id = $1', [req.params.id]);
  } else if (!left.rows.some((r) => r.role === 'admin')) {
    await query("UPDATE chat_members SET role = 'admin' WHERE chat_id = $1 AND user_id = $2", [req.params.id, left.rows[0].user_id]);
  }
  res.json({ ok: true });
}));

// Riwayat pesan (terbaru -> lama). Paginasi: ?before_id=<id pesan tertua yang sudah dimuat>&limit=50
router.get('/:id/messages', wrap(async (req, res) => {
  await requireMember(req.params.id, req.userId);
  const limit = Math.min(Math.max(parseInt(req.query.limit, 10) || 50, 1), 100);
  const rows = await listMessages(req.params.id, req.userId, { beforeId: req.query.before_id, limit });
  res.json({ messages: rows, has_more: rows.length === limit });
}));

// Kirim pesan via REST (alternatif dari event socket "message:send")
router.post('/:id/messages', wrap(async (req, res) => {
  const msg = await sendMessage(req.app.get('io'), {
    chatId: req.params.id,
    senderId: req.userId,
    body: req.body.body,
    type: req.body.type,
    mediaId: req.body.media_id,
    replyTo: req.body.reply_to,
    clientId: req.body.client_id,
  });
  res.status(201).json(msg);
}));

// Tandai semua pesan di chat sebagai dibaca
router.post('/:id/read', wrap(async (req, res) => {
  const count = await markRead(req.app.get('io'), req.params.id, req.userId);
  res.json({ ok: true, marked: count });
}));

module.exports = router;
