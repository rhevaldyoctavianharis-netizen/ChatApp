require('dotenv').config();

const isProd = process.env.NODE_ENV === 'production';
if (isProd && !process.env.JWT_SECRET) {
  throw new Error('JWT_SECRET wajib diisi di production');
}

const rawOrigin = process.env.CORS_ORIGIN || '*';

module.exports = {
  isProd,
  port: Number(process.env.PORT || 3000),
  jwtSecret: process.env.JWT_SECRET || 'dev-secret-change-me',
  corsOrigin: rawOrigin === '*' ? true : rawOrigin.split(',').map((s) => s.trim()),
  maxMediaMb: Number(process.env.MAX_MEDIA_MB || 16),
  redisUrl: process.env.REDIS_URL || null,
};
