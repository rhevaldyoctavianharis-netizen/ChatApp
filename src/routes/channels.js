const router = require('express').Router();
const { query } = require('../db');
const { HttpError, wrap } = require('../utils/errors');

const channelView = `
  SELECT c.id, c.owner_id, c.name, c.description, c.handle, c.avatar_media_id,
         c.created_at, (cf.user_id IS NOT NULL) AS following,
         (SELECT COUNT(*) FROM channel_followers f WHERE f.channel_id = c.id)::int AS followers
  FROM channels c
  LEFT JOIN channel_followers cf ON cf.channel_id = c.id AND cf.user_id = $1
`;

router.get('/', wrap(async (req, res) => {
  const q = String(req.query.q || '').trim();
  const args = [req.userId];
  const filter = q ? ' WHERE c.name ILIKE $2 OR c.handle ILIKE $2' : '';
  if (q) args.push(`%${q.replace(/[%_\\]/g, '\\$&')}%`);
  const { rows } = await query(`${channelView}${filter} ORDER BY c.created_at DESC LIMIT 100`, args);
  res.json(rows);
}));

router.post('/', wrap(async (req, res) => {
  const name = String(req.body.name || '').trim();
  const description = String(req.body.description || '').trim();
  const handle = String(req.body.handle || '').trim().toLowerCase();
  if (!name || name.length > 80) throw new HttpError(400, 'Nama channel 1-80 karakter');
  if (description.length > 500) throw new HttpError(400, 'Deskripsi maksimal 500 karakter');
  if (!/^[a-z0-9_]{3,40}$/.test(handle)) throw new HttpError(400, 'Handle channel tidak valid');
  try {
    const { rows } = await query(
      'INSERT INTO channels (owner_id, name, description, handle) VALUES ($1,$2,$3,$4) RETURNING id',
      [req.userId, name, description, handle],
    );
    await query('INSERT INTO channel_followers (channel_id, user_id) VALUES ($1,$2)', [rows[0].id, req.userId]);
    res.status(201).json((await query(`${channelView} WHERE c.id = $2`, [req.userId, rows[0].id])).rows[0]);
  } catch (e) {
    if (e.code === '23505') throw new HttpError(409, 'Handle channel sudah digunakan');
    throw e;
  }
}));

router.post('/:id/follow', wrap(async (req, res) => {
  const channel = await query('SELECT id FROM channels WHERE id = $1', [req.params.id]);
  if (!channel.rows[0]) throw new HttpError(404, 'Channel tidak ditemukan');
  await query(
    'INSERT INTO channel_followers (channel_id, user_id) VALUES ($1,$2) ON CONFLICT DO NOTHING',
    [req.params.id, req.userId],
  );
  res.json({ ok: true, following: true });
}));

router.delete('/:id/follow', wrap(async (req, res) => {
  await query('DELETE FROM channel_followers WHERE channel_id = $1 AND user_id = $2', [req.params.id, req.userId]);
  res.json({ ok: true, following: false });
}));

router.get('/:id/posts', wrap(async (req, res) => {
  const channel = await query('SELECT id FROM channels WHERE id = $1', [req.params.id]);
  if (!channel.rows[0]) throw new HttpError(404, 'Channel tidak ditemukan');
  const limit = Math.min(Math.max(Number(req.query.limit) || 50, 1), 100);
  const { rows } = await query(
    `SELECT id, channel_id, body, media_id, created_at
     FROM channel_posts WHERE channel_id = $1 ORDER BY created_at DESC LIMIT $2`,
    [req.params.id, limit],
  );
  res.json(rows);
}));

router.post('/:id/posts', wrap(async (req, res) => {
  const body = String(req.body.body || '').trim();
  if (!body || body.length > 4096) throw new HttpError(400, 'Posting 1-4096 karakter');
  const owner = await query('SELECT owner_id FROM channels WHERE id = $1', [req.params.id]);
  if (!owner.rows[0]) throw new HttpError(404, 'Channel tidak ditemukan');
  if (owner.rows[0].owner_id !== req.userId) throw new HttpError(403, 'Hanya pemilik channel yang dapat posting');
  if (req.body.media_id) {
    const media = await query('SELECT 1 FROM media WHERE id = $1 AND owner_id = $2', [req.body.media_id, req.userId]);
    if (!media.rows[0]) throw new HttpError(400, 'Media tidak valid');
  }
  const { rows } = await query(
    'INSERT INTO channel_posts (channel_id, body, media_id) VALUES ($1,$2,$3) RETURNING id, channel_id, body, media_id, created_at',
    [req.params.id, body, req.body.media_id || null],
  );
  res.status(201).json(rows[0]);
}));

module.exports = router;
