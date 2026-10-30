const jwt = require('jsonwebtoken');
const { jwtSecret } = require('../config');
const { HttpError, wrap } = require('../utils/errors');
const { query } = require('../db');

const signToken = (userId) => jwt.sign({ sub: userId }, jwtSecret, { expiresIn: '30d' });
const verifyToken = (token) => jwt.verify(token, jwtSecret);

// Token dari header "Authorization: Bearer xxx" atau ?token=xxx (untuk <img src>)
const auth = wrap(async (req, _res, next) => {
  const h = req.headers.authorization || '';
  const token = h.startsWith('Bearer ') ? h.slice(7) : req.query.token;
  if (!token) throw new HttpError(401, 'Token tidak ada');
  let payload;
  try {
    payload = verifyToken(token);
  } catch {
    throw new HttpError(401, 'Token tidak valid atau kedaluwarsa');
  }
  const { rows } = await query('SELECT id FROM users WHERE id = $1', [payload.sub]);
  if (!rows[0]) throw new HttpError(401, 'Pengguna tidak ditemukan');
  req.userId = payload.sub;
  next();
});

module.exports = { auth, signToken, verifyToken };
