const jwt = require('jsonwebtoken');
const { jwtSecret } = require('../config');
const { HttpError, wrap } = require('../utils/errors');
const { query } = require('../db');

const signToken = (userId) => jwt.sign({ sub: userId }, jwtSecret, { expiresIn: '30d' });
const verifyToken = (token) => jwt.verify(token, jwtSecret);

// Token dari header "Authorization: Bearer xxx" atau ?token=xxx (untuk <img src>)
const auth = wrap(async (req, _res, next) => {
  const h = req.headers.authorization || '';
  const queryToken = req.baseUrl === '/api/media' ? req.query.token : null;
  const token = h.startsWith('Bearer ') ? h.slice(7) : queryToken;
  if (!token) throw new HttpError(401, 'Token tidak ada');
  let payload;
  try {
    payload = verifyToken(token);
  } catch {
    throw new HttpError(401, 'Token tidak valid atau kedaluwarsa');
  }
  const { rows } = await query('SELECT id, is_banned FROM users WHERE id = $1', [payload.sub]);
  if (!rows[0]) throw new HttpError(401, 'Pengguna tidak ditemukan');
  if (rows[0].is_banned) throw new HttpError(403, 'Akun diblokir');
  req.userId = payload.sub;
  next();
});

const admin = wrap(async (req, _res, next) => {
  const { rows } = await query('SELECT id FROM users WHERE id = $1 AND badges @> $2::jsonb', [req.userId, JSON.stringify(['admin'])]);
  if (!rows[0]) throw new HttpError(403, 'Akses admin diperlukan');
  next();
});

module.exports = { auth, admin, signToken, verifyToken };
