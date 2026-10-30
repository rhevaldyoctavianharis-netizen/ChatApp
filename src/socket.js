const { verifyToken } = require('./middleware/auth');
const { query } = require('./db');
const { sendMessage, markRead, markDeliveredForUser, isOnline } = require('./services/messages');

module.exports = function registerSocket(io) {
  // Autentikasi: io(url, { auth: { token } })
  io.use(async (socket, next) => {
    try {
      const raw = socket.handshake.auth?.token || (socket.handshake.headers.authorization || '').replace('Bearer ', '');
      const payload = verifyToken(raw);
      const { rows } = await query('SELECT id FROM users WHERE id = $1', [payload.sub]);
      if (!rows[0]) throw new Error('no user');
      socket.userId = payload.sub;
      next();
    } catch {
      next(new Error('unauthorized'));
    }
  });

  const safe = (handler) => async (payload, ack) => {
    const cb = typeof ack === 'function' ? ack : () => {};
    try {
      cb({ ok: true, ...(await handler(payload || {})) });
    } catch (e) {
      cb({ ok: false, error: e.status ? e.message : 'Terjadi kesalahan server' });
      if (!e.status) console.error('[socket]', e);
    }
  };

  io.on('connection', async (socket) => {
    const uid = socket.userId;
    try {
      socket.join(`user:${uid}`);
      const { rows } = await query('SELECT chat_id FROM chat_members WHERE user_id = $1', [uid]);
      const rooms = rows.map((r) => `chat:${r.chat_id}`);
      if (rooms.length) socket.join(rooms);

      const count = (await io.in(`user:${uid}`).fetchSockets()).length;
      if (count === 1 && rooms.length) {
        socket.to(rooms).emit('presence', { user_id: uid, online: true, last_seen: null });
      }
      await markDeliveredForUser(io, uid);
    } catch (e) {
      console.error('[socket] connect error', e);
    }

    // Kirim pesan: { chat_id, body?, media_id?, reply_to?, client_id? }
    socket.on('message:send', safe(async (p) => {
      const message = await sendMessage(io, {
        chatId: p.chat_id, senderId: uid, body: p.body,
        mediaId: p.media_id, replyTo: p.reply_to, clientId: p.client_id, type: p.type,
      });
      return { message };
    }));

    // Tandai dibaca: { chat_id }
    socket.on('message:read', safe(async (p) => ({ marked: await markRead(io, p.chat_id, uid) })));

    // Sedang mengetik: { chat_id, is_typing }
    socket.on('typing', (p) => {
      if (!p || !socket.rooms.has(`chat:${p.chat_id}`)) return;
      socket.to(`chat:${p.chat_id}`).emit('typing', { chat_id: p.chat_id, user_id: uid, is_typing: !!p.is_typing });
    });

    // Cek status online: { user_ids: [] }
    socket.on('presence:query', safe(async (p) => {
      const ids = (Array.isArray(p.user_ids) ? p.user_ids : []).slice(0, 100);
      const online = [];
      for (const id of ids) if (await isOnline(io, id)) online.push(id);
      return { online };
    }));

    socket.on('disconnecting', async () => {
      try {
        const rooms = [...socket.rooms].filter((r) => r.startsWith('chat:'));
        const remaining = (await io.in(`user:${uid}`).fetchSockets()).filter((s) => s.id !== socket.id).length;
        if (remaining === 0) {
          const { rows } = await query('UPDATE users SET last_seen = now() WHERE id = $1 RETURNING last_seen', [uid]);
          if (rooms.length) io.to(rooms).emit('presence', { user_id: uid, online: false, last_seen: rows[0].last_seen });
        }
      } catch (e) {
        console.error('[socket] disconnect error', e.message);
      }
    });
  });
};
