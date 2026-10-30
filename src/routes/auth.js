const router = require('express').Router();
const bcrypt = require('bcryptjs');
const crypto = require('crypto');
const rateLimit = require('express-rate-limit');
const { query } = require('../db');
const { signToken } = require('../middleware/auth');
const { HttpError, wrap, normalizePhone, toUser } = require('../utils/errors');
const config = require('../config');

const limiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  limit: 30,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Terlalu banyak percobaan, coba lagi nanti' },
});

const otpLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  limit: 5,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Terlalu banyak permintaan OTP' },
});

const hash = (value) => crypto.createHmac('sha256', config.deviceHashSecret).update(String(value)).digest('hex');
const validEmail = (email) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);

router.post('/request-otp', otpLimiter, wrap(async (req, res) => {
  const email = String(req.body.email || '').trim().toLowerCase();
  const purpose = req.body.purpose === 'register' ? 'register' : 'login';
  if (!validEmail(email)) throw new HttpError(400, 'Email tidak valid');
  if (!config.brevoApiKey || !config.brevoSenderEmail) throw new HttpError(503, 'OTP belum dikonfigurasi server');
  const code = String(crypto.randomInt(100000, 1000000));
  await query('UPDATE otp_codes SET used_at = now() WHERE email = $1 AND used_at IS NULL', [email]);
  await query(
    `INSERT INTO otp_codes (email, code_hash, purpose, device_hash, expires_at)
     VALUES ($1,$2,$3,$4,now() + interval '10 minutes')`,
    [email, hash(code), purpose, req.get('x-device-id') ? hash(req.get('x-device-id')) : null],
  );
  const response = await fetch('https://api.brevo.com/v3/smtp/email', {
    method: 'POST',
    headers: { accept: 'application/json', 'content-type': 'application/json', 'api-key': config.brevoApiKey },
    body: JSON.stringify({
      sender: { email: config.brevoSenderEmail, name: config.brevoSenderName },
      to: [{ email }],
      subject: 'Kode OTP ChatKu',
      htmlContent: `<p>Kode OTP ChatKu Anda: <strong>${code}</strong></p><p>Berlaku 10 menit.</p>`,
    }),
  });
  if (!response.ok) throw new HttpError(502, 'Pengiriman OTP gagal');
  res.json({ ok: true, expires_in: 600 });
}));

router.post('/verify-otp', otpLimiter, wrap(async (req, res) => {
  const email = String(req.body.email || '').trim().toLowerCase();
  const code = String(req.body.code || '');
  const displayName = String(req.body.display_name || 'Pengguna ChatKu').trim().slice(0, 50);
  if (!validEmail(email) || !/^\d{6}$/.test(code)) throw new HttpError(400, 'OTP tidak valid');
  const { rows } = await query(
    `SELECT * FROM otp_codes WHERE email = $1 AND used_at IS NULL AND expires_at > now()
     ORDER BY created_at DESC LIMIT 1`,
    [email],
  );
  if (!rows[0] || rows[0].attempts >= 5 || rows[0].code_hash !== hash(code)) {
    if (rows[0]) await query('UPDATE otp_codes SET attempts = attempts + 1 WHERE id = $1', [rows[0].id]);
    throw new HttpError(401, 'OTP salah atau kedaluwarsa');
  }
  await query('UPDATE otp_codes SET used_at = now() WHERE id = $1', [rows[0].id]);
  let user = (await query('SELECT * FROM users WHERE email = $1', [email])).rows[0];
  if (!user) {
    const passwordHash = await bcrypt.hash(crypto.randomBytes(32).toString('hex'), 12);
    user = (await query(
      `INSERT INTO users (phone, email, display_name, password_hash)
       VALUES ($1,$2,$3,$4) RETURNING *`,
      [`email:${email}`, email, displayName || 'Pengguna ChatKu', passwordHash],
    )).rows[0];
  }
  res.json({ token: signToken(user.id), user: toUser(user) });
}));

router.post('/anonymous', otpLimiter, wrap(async (req, res) => {
  const deviceId = String(req.get('x-device-id') || '').trim();
  if (deviceId.length < 8 || deviceId.length > 200) throw new HttpError(400, 'Identitas perangkat diperlukan');
  const deviceHash = hash(deviceId);
  const found = await query('SELECT user_id FROM anonymous_devices WHERE device_hash = $1', [deviceHash]);
  if (found.rows[0]) throw new HttpError(409, 'Perangkat ini sudah pernah memakai akses anonim');
  const info = typeof req.body.device_info === 'object' && req.body.device_info ? req.body.device_info : {};
  const passwordHash = await bcrypt.hash(crypto.randomBytes(32).toString('hex'), 12);
  const user = (await query(
    `INSERT INTO users (phone, display_name, password_hash, is_anonymous)
     VALUES ($1,'Pengguna anonim',$2,true) RETURNING *`,
    [`anonymous:${deviceHash}`, passwordHash],
  )).rows[0];
  await query(
    `INSERT INTO anonymous_devices (device_hash, user_id, ip_hash, user_agent, device_info)
     VALUES ($1,$2,$3,$4,$5)`,
    [deviceHash, user.id, hash(req.ip), String(req.get('user-agent') || '').slice(0, 500), JSON.stringify(info)],
  );
  res.status(201).json({ token: signToken(user.id), user: toUser(user) });
}));

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
