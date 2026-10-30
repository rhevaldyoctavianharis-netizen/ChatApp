const router = require('express').Router();
const bcrypt = require('bcryptjs');
const rateLimit = require('express-rate-limit');
const { query } = require('../db');
const { signToken } = require('../middleware/auth');
const { HttpError, wrap, normalizePhone, toUser } = require('../utils/errors');

const limiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  limit: 30,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Terlalu banyak percobaan, coba lagi nanti' },
});

router.post('/register', limiter, wrap(async (req, res) => {
  const phone = normalizePhone(req.body.phone);
  const displayName = String(req.body.display_name || '').trim();
  const password = String(req.body.password || '');
  if (!phone) throw new HttpError(400, 'Nomor telepon harus format internasional, contoh +628123456789');
  if (displayName.length < 1 || displayName.length > 50) throw new HttpError(400, 'Nama 1-50 karakter');
  if (password.length < 6) throw new HttpError(400, 'Password minimal 6 karakter');

  const hash = await bcrypt.hash(password, 10);
  try {
    const { rows } = await query(
      'INSERT INTO users (phone, display_name, password_hash) VALUES ($1,$2,$3) RETURNING *',
      [phone, displayName, hash]
    );
    res.status(201).json({ token: signToken(rows[0].id), user: toUser(rows[0]) });
  } catch (e) {
    if (e.code === '23505') throw new HttpError(409, 'Nomor sudah terdaftar');
    throw e;
  }
}));

router.post('/login', limiter, wrap(async (req, res) => {
  const phone = normalizePhone(req.body.phone);
  const password = String(req.body.password || '');
  if (!phone) throw new HttpError(400, 'Nomor telepon tidak valid');
  const { rows } = await query('SELECT * FROM users WHERE phone = $1', [phone]);
  const user = rows[0];
  if (!user || !(await bcrypt.compare(password, user.password_hash))) {
    throw new HttpError(401, 'Nomor atau password salah');
  }
  res.json({ token: signToken(user.id), user: toUser(user) });
}));

module.exports = router;
