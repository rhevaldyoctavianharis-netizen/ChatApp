const router = require('express').Router();
const { query } = require('../db');
const { HttpError, wrap, normalizePhone, toUser } = require('../utils/errors');
const { isOnline } = require('../services/messages');

// Profil sendiri
router.get('/me', wrap(async (req, res) => {
  const { rows } = await query('SELECT * FROM users WHERE id = $1', [req.userId]);
  res.json(toUser(rows[0]));
}));

router.patch('/me', wrap(async (req, res) => {
  const { display_name, about, avatar_media_id } = req.body;
  if (display_name !== undefined && (!String(display_name).trim() || String(display_name).length > 50)) {
    throw new HttpError(400, 'Nama 1-50 karakter');
  }
  if (about !== undefined && String(about).length > 140) throw new HttpError(400, 'Info maks 140 karakter');
  if (avatar_media_id) {
    const m = await query("SELECT 1 FROM media WHERE id = $1 AND owner_id = $2 AND mime LIKE 'image/%'", [avatar_media_id, req.userId]);
    if (!m.rows[0]) throw new HttpError(400, 'Media avatar tidak valid');
  }
  const { rows } = await query(
    `UPDATE users SET
       display_name = COALESCE($2, display_name),
       about = COALESCE($3, about),
       avatar_media_id = COALESCE($4, avatar_media_id)
     WHERE id = $1 RETURNING *`,
    [req.userId, display_name ? String(display_name).trim() : null, about ?? null, avatar_media_id || null]
  );
  res.json(toUser(rows[0]));
}));

// Daftarkan token push (Expo)
router.post('/me/push-token', wrap(async (req, res) => {
  const token = String(req.body.token || '');
  if (!token) throw new HttpError(400, 'token wajib');
  await query(
    `INSERT INTO push_tokens (token, user_id, platform) VALUES ($1,$2,$3)
     ON CONFLICT (token) DO UPDATE SET user_id = EXCLUDED.user_id, platform = EXCLUDED.platform`,
    [token, req.userId, req.body.platform || null]
  );
  res.json({ ok: true });
}));

router.delete('/me/push-token', wrap(async (req, res) => {
  await query('DELETE FROM push_tokens WHERE token = $1 AND user_id = $2', [String(req.body.token || ''), req.userId]);
  res.json({ ok: true });
}));

// Cari pengguna berdasarkan nama / nomor
router.get('/users/search', wrap(async (req, res) => {
  const q = String(req.query.q || '').trim();
  if (q.length < 3) throw new HttpError(400, 'Kata kunci minimal 3 karakter');
  const like = `%${q.replace(/[%_\\]/g, '\\$&')}%`;
  const { rows } = await query(
    `SELECT * FROM users WHERE id <> $1 AND (phone ILIKE $2 OR display_name ILIKE $2)
     ORDER BY display_name LIMIT 20`,
    [req.userId, like]
  );
  res.json(rows.map(toUser));
}));

// Sinkronisasi kontak: kirim daftar nomor, dapatkan yang terdaftar
router.post('/contacts/match', wrap(async (req, res) => {
  const phones = Array.isArray(req.body.phones) ? req.body.phones : [];
  if (phones.length > 500) throw new HttpError(400, 'Maksimal 500 nomor per request');
  const list = [...new Set(phones.map(normalizePhone).filter(Boolean))];
  if (!list.length) return res.json([]);
  const { rows } = await query('SELECT * FROM users WHERE phone = ANY($1::text[]) AND id <> $2', [list, req.userId]);
  res.json(rows.map(toUser));
}));

router.get('/users/:id', wrap(async (req, res) => {
  const { rows } = await query('SELECT * FROM users WHERE id = $1', [req.params.id]);
  if (!rows[0]) throw new HttpError(404, 'Pengguna tidak ditemukan');
  const io = req.app.get('io');
  res.json({ ...toUser(rows[0]), online: await isOnline(io, rows[0].id) });
}));

module.exports = router;
