const router = require('express').Router();
const { query } = require('../db');
const { HttpError, wrap } = require('../utils/errors');

const activeWhere = 's.expires_at > now()';

router.get('/', wrap(async (req, res) => {
  const { rows } = await query(
    `SELECT s.id, s.user_id, u.display_name, u.avatar_media_id, s.body, s.media_id,
            s.created_at, s.expires_at,
            (sv.viewer_id IS NOT NULL) AS viewed
     FROM statuses s
     JOIN users u ON u.id = s.user_id
     LEFT JOIN status_views sv ON sv.status_id = s.id AND sv.viewer_id = $1
     WHERE ${activeWhere}
       AND (s.user_id = $1 OR EXISTS (
         SELECT 1 FROM chat_members mine
         JOIN chat_members other ON other.chat_id = mine.chat_id
         WHERE mine.user_id = $1 AND other.user_id = s.user_id
       ))
     ORDER BY s.created_at DESC
     LIMIT 200`,
    [req.userId],
  );
  res.json(rows);
}));

router.post('/', wrap(async (req, res) => {
  const body = String(req.body.body || '').trim();
  if (!body || body.length > 700) throw new HttpError(400, 'Status 1-700 karakter');
  if (req.body.media_id) {
    const media = await query('SELECT 1 FROM media WHERE id = $1 AND owner_id = $2', [req.body.media_id, req.userId]);
    if (!media.rows[0]) throw new HttpError(400, 'Media tidak valid');
  }
  const { rows } = await query(
    `INSERT INTO statuses (user_id, body, media_id)
     VALUES ($1,$2,$3)
     RETURNING id, user_id, body, media_id, created_at, expires_at`,
    [req.userId, body, req.body.media_id || null],
  );
  res.status(201).json(rows[0]);
}));

router.post('/:id/view', wrap(async (req, res) => {
  const status = await query(`SELECT id FROM statuses WHERE id = $1 AND ${activeWhere}`, [req.params.id]);
  if (!status.rows[0]) throw new HttpError(404, 'Status tidak ditemukan atau sudah kedaluwarsa');
  await query(
    `INSERT INTO status_views (status_id, viewer_id) VALUES ($1,$2)
     ON CONFLICT (status_id, viewer_id) DO UPDATE SET viewed_at = now()`,
    [req.params.id, req.userId],
  );
  res.json({ ok: true });
}));

module.exports = router;
