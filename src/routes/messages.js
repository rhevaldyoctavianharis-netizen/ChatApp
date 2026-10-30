const router = require('express').Router();
const { query } = require('../db');
const { HttpError, wrap } = require('../utils/errors');
const { requireMember } = require('../services/chats');
const { getMessage } = require('../services/messages');

async function loadOwn(id, userId) {
  const { rows } = await query('SELECT id, chat_id, sender_id, type, deleted_at FROM messages WHERE id = $1', [id]);
  const m = rows[0];
  if (!m) throw new HttpError(404, 'Pesan tidak ditemukan');
  await requireMember(m.chat_id, userId);
  return m;
}

// Edit pesan teks milik sendiri
router.patch('/:id', wrap(async (req, res) => {
  const m = await loadOwn(req.params.id, req.userId);
  if (m.sender_id !== req.userId) throw new HttpError(403, 'Hanya pengirim yang bisa mengedit');
  if (m.deleted_at) throw new HttpError(400, 'Pesan sudah dihapus');
  const body = String(req.body.body || '').trim();
  if (!body || body.length > 4096) throw new HttpError(400, 'Isi pesan 1-4096 karakter');
  await query('UPDATE messages SET body = $2, edited_at = now() WHERE id = $1', [m.id, body]);
  const msg = await getMessage(m.id, req.userId);
  req.app.get('io').to(`chat:${m.chat_id}`).emit('message:edited', msg);
  res.json(msg);
}));

// Hapus untuk semua orang (soft delete)
router.delete('/:id', wrap(async (req, res) => {
  const m = await loadOwn(req.params.id, req.userId);
  if (m.sender_id !== req.userId) throw new HttpError(403, 'Hanya pengirim yang bisa menghapus');
  await query('UPDATE messages SET deleted_at = now() WHERE id = $1 AND deleted_at IS NULL', [m.id]);
  req.app.get('io').to(`chat:${m.chat_id}`).emit('message:deleted', { chat_id: m.chat_id, message_id: m.id });
  res.json({ ok: true });
}));

// Beri / ubah reaksi emoji
router.put('/:id/reactions', wrap(async (req, res) => {
  const m = await loadOwn(req.params.id, req.userId);
  const emoji = String(req.body.emoji || '').trim();
  if (!emoji || [...emoji].length > 8) throw new HttpError(400, 'emoji tidak valid');
  await query(
    `INSERT INTO message_reactions (message_id, user_id, emoji) VALUES ($1,$2,$3)
     ON CONFLICT (message_id, user_id) DO UPDATE SET emoji = EXCLUDED.emoji, created_at = now()`,
    [m.id, req.userId, emoji]);
  req.app.get('io').to(`chat:${m.chat_id}`).emit('message:reaction',
    { chat_id: m.chat_id, message_id: m.id, user_id: req.userId, emoji });
  res.json({ ok: true });
}));

router.delete('/:id/reactions', wrap(async (req, res) => {
  const m = await loadOwn(req.params.id, req.userId);
  await query('DELETE FROM message_reactions WHERE message_id = $1 AND user_id = $2', [m.id, req.userId]);
  req.app.get('io').to(`chat:${m.chat_id}`).emit('message:reaction',
    { chat_id: m.chat_id, message_id: m.id, user_id: req.userId, emoji: null });
  res.json({ ok: true });
}));

// Info "dibaca oleh / terkirim ke" (hanya pengirim)
router.get('/:id/receipts', wrap(async (req, res) => {
  const m = await loadOwn(req.params.id, req.userId);
  if (m.sender_id !== req.userId) throw new HttpError(403, 'Hanya pengirim');
  const { rows } = await query(
    `SELECT r.user_id, u.display_name, r.delivered_at, r.read_at
     FROM message_receipts r JOIN users u ON u.id = r.user_id WHERE r.message_id = $1`, [m.id]);
  res.json(rows);
}));

module.exports = router;
