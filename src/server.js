const http = require('http');
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const compression = require('compression');
const morgan = require('morgan');
const { Server } = require('socket.io');

const config = require('./config');
const { migrate } = require('./migrate');
const { auth } = require('./middleware/auth');
const registerSocket = require('./socket');

// Domain publik diambil otomatis dari request (Heroku berada di belakang proxy),
// jadi tidak perlu dikonfigurasi manual. Fallback: metadata dyno Heroku bila diaktifkan.
function publicBaseUrl(req) {
  const herokuDomain = process.env.HEROKU_APP_DEFAULT_DOMAIN_NAME;
  const host = req.get('host') || herokuDomain;
  if (!host) return null;
  return `${req.protocol}://${host}`;
}

async function main() {
  await migrate();

  const app = express();
  app.set('trust proxy', 1); // Heroku berada di belakang proxy
  app.use(helmet({ crossOriginResourcePolicy: { policy: 'cross-origin' } }));
  app.use(cors({ origin: config.corsOrigin }));
  app.use(compression());
  app.use(express.json({ limit: '1mb' }));
  if (!config.isProd) app.use(morgan('dev'));

  app.get('/', (req, res) => {
    const base = publicBaseUrl(req);
    res.json({
      name: 'chat-backend',
      status: 'ok',
      base_url: base,
      websocket_url: base ? base.replace(/^http/, 'ws') : null,
      health: '/health',
    });
  });
  // Dipakai klien (mis. aplikasi Android) untuk mengetahui BASE_URL server ini
  app.get('/api/info', (req, res) => {
    const base = publicBaseUrl(req);
    res.json({ name: 'chat-backend', base_url: base, websocket_url: base ? base.replace(/^http/, 'ws') : null });
  });
  app.get('/health', (_req, res) => res.json({ status: 'ok', time: new Date().toISOString() }));

  app.use('/api/auth', require('./routes/auth'));
  app.use('/api/media', require('./routes/media'));
  app.use('/api/chats', auth, require('./routes/chats'));
  app.use('/api/channels', auth, require('./routes/channels'));
  app.use('/api/statuses', auth, require('./routes/statuses'));
  app.use('/api/messages', auth, require('./routes/messages'));
  app.use('/api', auth, require('./routes/users'));

  app.use((_req, res) => res.status(404).json({ error: 'Endpoint tidak ditemukan' }));

  // eslint-disable-next-line no-unused-vars
  app.use((err, _req, res, _next) => {
    if (err.code === '22P02') return res.status(400).json({ error: 'Format ID tidak valid' });
    if (err.code === 'LIMIT_FILE_SIZE') return res.status(413).json({ error: `File melebihi ${config.maxMediaMb} MB` });
    if (err.type === 'entity.parse.failed') return res.status(400).json({ error: 'JSON tidak valid' });
    const status = err.status || 500;
    if (status >= 500) console.error(err);
    res.status(status).json({ error: status >= 500 ? 'Terjadi kesalahan server' : err.message });
  });

  const server = http.createServer(app);
  const io = new Server(server, {
    cors: { origin: config.corsOrigin, methods: ['GET', 'POST'] },
    maxHttpBufferSize: 1e6,
    pingInterval: 25000,
    pingTimeout: 20000,
  });
  app.set('io', io);

  // Redis adapter: diperlukan bila menjalankan lebih dari 1 dyno web
  if (config.redisUrl) {
    const { createAdapter } = require('@socket.io/redis-adapter');
    const { createClient } = require('redis');
    const pub = createClient({
      url: config.redisUrl,
      socket: config.redisUrl.startsWith('rediss://')
        ? {
            tls: true,
            rejectUnauthorized: true,
            ...(process.env.REDIS_TLS_CA ? { ca: process.env.REDIS_TLS_CA } : {}),
          }
        : undefined,
    });
    const sub = pub.duplicate();
    pub.on('error', (e) => console.error('[redis]', e.message));
    sub.on('error', (e) => console.error('[redis]', e.message));
    await Promise.all([pub.connect(), sub.connect()]);
    io.adapter(createAdapter(pub, sub));
    console.log('[socket.io] Redis adapter aktif');
  }

  registerSocket(io);

  server.listen(config.port, '0.0.0.0', () => console.log(`[server] berjalan di port ${config.port}`));

  const shutdown = () => {
    console.log('[server] shutting down...');
    io.close(() => process.exit(0));
    setTimeout(() => process.exit(0), 8000).unref();
  };
  process.on('SIGTERM', shutdown);
  process.on('SIGINT', shutdown);
}

main().catch((e) => {
  console.error('Gagal start server:', e);
  process.exit(1);
});
