require('dotenv').config();

const isProd = process.env.NODE_ENV === 'production';
if (!process.env.JWT_SECRET || process.env.JWT_SECRET.length < 32) {
  throw new Error('JWT_SECRET wajib diisi dan minimal 32 karakter');
}

const rawOrigin = process.env.CORS_ORIGIN || '*';

module.exports = {
  isProd,
  port: Number(process.env.PORT || 3000),
  jwtSecret: process.env.JWT_SECRET,
  corsOrigin: rawOrigin === '*' ? true : rawOrigin.split(',').map((s) => s.trim()),
  maxMediaMb: Number(process.env.MAX_MEDIA_MB || 16),
  redisUrl: process.env.REDIS_URL || null,
  brevoApiKey: process.env.BREVO_API_KEY || null,
  brevoSenderEmail: process.env.BREVO_SENDER_EMAIL || null,
  brevoSenderName: process.env.BREVO_SENDER_NAME || 'ChatKu',
  adminKey: process.env.ADMIN_DASHBOARD_KEY || null,
  deviceHashSecret: process.env.DEVICE_HASH_SECRET || process.env.JWT_SECRET,
  adminEmail: process.env.ADMIN_EMAIL || null,
};
