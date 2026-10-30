const { query } = require('../db');
const { HttpError } = require('../utils/errors');

async function requireMember(chatId, userId) {
  const { rows } = await query('SELECT role, muted FROM chat_members WHERE chat_id = $1 AND user_id = $2', [chatId, userId]);
  if (!rows[0]) throw new HttpError(403, 'Anda bukan anggota chat ini');
  return rows[0];
}

async function getMemberIds(chatId) {
  const { rows } = await query('SELECT user_id FROM chat_members WHERE chat_id = $1', [chatId]);
  return rows.map((r) => r.user_id);
}

const CHAT_SELECT = `
SELECT c.id, c.type, c.name, c.description, c.message_ttl_seconds, c.is_public, c.public_handle, c.community_name, c.created_at, cm.role, cm.muted,
  (SELECT COUNT(*) FROM message_receipts r JOIN messages m ON m.id = r.message_id
     WHERE m.chat_id = c.id AND r.user_id = $1 AND r.read_at IS NULL AND m.deleted_at IS NULL)::int AS unread,
  CASE WHEN c.type = 'direct' THEN (
    SELECT json_build_object('id', u.id, 'display_name', u.display_name, 'phone', u.phone,
                             'about', u.about, 'avatar_media_id', u.avatar_media_id, 'last_seen', u.last_seen)
    FROM chat_members o JOIN users u ON u.id = o.user_id
    WHERE o.chat_id = c.id AND o.user_id <> $1 LIMIT 1) END AS peer,
  (SELECT row_to_json(lm) FROM (
     SELECT m.id, m.sender_id, m.type,
            CASE WHEN m.deleted_at IS NULL THEN left(m.body, 200) END AS body,
            (m.deleted_at IS NOT NULL) AS deleted, m.created_at
     FROM messages m WHERE m.chat_id = c.id
       AND (c.message_ttl_seconds = 0 OR m.created_at + (c.message_ttl_seconds || ' seconds')::interval > now())
       ORDER BY m.created_at DESC LIMIT 1) lm) AS last_message,
  COALESCE((SELECT MAX(created_at) FROM messages WHERE chat_id = c.id), c.created_at) AS last_activity
FROM chats c
JOIN chat_members cm ON cm.chat_id = c.id AND cm.user_id = $1
`;

async function listChats(userId) {
  const { rows } = await query(`${CHAT_SELECT} ORDER BY last_activity DESC`, [userId]);
  return rows;
}

async function getChat(chatId, userId) {
  const { rows } = await query(`${CHAT_SELECT} WHERE c.id = $2`, [userId, chatId]);
  if (!rows[0]) throw new HttpError(404, 'Chat tidak ditemukan');
  const chat = rows[0];
  if (chat.type === 'group') {
    const m = await query(
      `SELECT u.id, u.display_name, u.phone, u.avatar_media_id, u.about, cm.role
       FROM chat_members cm JOIN users u ON u.id = cm.user_id
       WHERE cm.chat_id = $1 ORDER BY cm.joined_at`, [chatId]);
    chat.members = m.rows;
  }
  return chat;
}

module.exports = { requireMember, getMemberIds, listChats, getChat };
