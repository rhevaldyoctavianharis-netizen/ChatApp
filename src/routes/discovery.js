const router = require('express').Router();
const { query } = require('../db');
const { HttpError, wrap } = require('../utils/errors');
const { requireMember, getChat } = require('../services/chats');

router.get('/groups', wrap(async (req, res) => {
  const q = String(req.query.q || '').trim().toLowerCase();
  const limit = Math.min(Math.max(Number(req.query.limit) || 30, 1), 100);
  const { rows } = await query(
    `SELECT c.id, c.name, c.description, c.public_handle, c.community_name,
            COUNT(cm.user_id)::int AS member_count
       FROM chats c LEFT JOIN chat_members cm ON cm.chat_id = c.id
      WHERE c.type = 'group' AND c.is_public = true
        AND ($1 = '' OR lower(c.name) LIKE '%' || $1 || '%'
             OR lower(COALESCE(c.description, '')) LIKE '%' || $1 || '%'
             OR lower(COALESCE(c.public_handle, '')) LIKE '%' || $1 || '%')
      GROUP BY c.id ORDER BY member_count DESC, c.created_at DESC LIMIT $2`,
    [q, limit],
  );
  res.json(rows);
}));

router.post('/groups/:id/join', wrap(async (req, res) => {
  const { rows } = await query(
    `SELECT id FROM chats WHERE id = $1 AND type = 'group' AND is_public = true`,
    [req.params.id],
  );
  if (!rows[0]) throw new HttpError(404, 'Grup publik tidak ditemukan');
  await query(
    `INSERT INTO chat_members (chat_id, user_id) VALUES ($1, $2) ON CONFLICT DO NOTHING`,
    [req.params.id, req.userId],
  );
  res.status(201).json(await getChat(req.params.id, req.userId));
}));

router.delete('/groups/:id/join', wrap(async (req, res) => {
  await requireMember(req.params.id, req.userId);
  await query('DELETE FROM chat_members WHERE chat_id = $1 AND user_id = $2', [req.params.id, req.userId]);
  res.json({ ok: true });
}));

module.exports = router;
