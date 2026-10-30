const router = require('express').Router();
const { query } = require('../db');
const { HttpError, wrap } = require('../utils/errors');
const config = require('../config');

const requireDashboardKey = (req, _res, next) => {
  if (!config.adminKey || req.get('x-admin-key') !== config.adminKey) throw new HttpError(403, 'Dashboard key tidak valid');
  next();
};

router.get('/dashboard', requireDashboardKey, wrap(async (_req, res) => {
  const [users, chats, messages, anonymous] = await Promise.all([
    query('SELECT COUNT(*)::int AS count FROM users'),
    query('SELECT COUNT(*)::int AS count FROM chats'),
    query('SELECT COUNT(*)::int AS count FROM messages'),
    query('SELECT COUNT(*)::int AS count FROM users WHERE is_anonymous = true'),
  ]);
  res.type('html').send(`<!doctype html><meta charset="utf-8"><title>ChatKu Admin</title>
    <style>body{font-family:system-ui;max-width:900px;margin:40px auto;padding:0 20px;background:#f5f7fb;color:#17202a}
    .grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}.card{background:white;border-radius:18px;padding:20px;box-shadow:0 8px 24px #17202a14}
    code{background:#eef2f7;padding:3px 6px;border-radius:6px}</style>
    <h1>ChatKu Admin</h1><p>Dashboard read-only. Gunakan REST admin API untuk tindakan.</p>
    <div class="grid">${[['Users', users], ['Chats', chats], ['Messages', messages], ['Anonymous', anonymous]].map(([n, r]) => `<div class="card"><small>${n}</small><h2>${r.rows[0].count}</h2></div>`).join('')}</div>`);
}));

router.get('/users', requireDashboardKey, wrap(async (req, res) => {
  const limit = Math.min(Math.max(Number(req.query.limit) || 100, 1), 500);
  const { rows } = await query(
    `SELECT id, email, display_name, is_anonymous, is_banned, premium_until, badges, created_at
     FROM users ORDER BY created_at DESC LIMIT $1`, [limit],
  );
  res.json(rows);
}));

router.patch('/users/:id/ban', requireDashboardKey, wrap(async (req, res) => {
  const banned = req.body.banned !== false;
  const { rows } = await query('UPDATE users SET is_banned = $2 WHERE id = $1 RETURNING id, is_banned', [req.params.id, banned]);
  if (!rows[0]) throw new HttpError(404, 'User tidak ditemukan');
  await query('INSERT INTO admin_audit_log (action, target_user_id, metadata) VALUES ($1,$2,$3)', [
    banned ? 'ban_user' : 'unban_user', req.params.id, JSON.stringify({ source: 'dashboard' }),
  ]);
  res.json(rows[0]);
}));

router.patch('/users/:id/premium', requireDashboardKey, wrap(async (req, res) => {
  const days = Math.min(Math.max(Number(req.body.days) || 0, 0), 3650);
  const { rows } = await query(
    `UPDATE users SET premium_until = CASE WHEN $2 = 0 THEN NULL ELSE now() + ($2 || ' days')::interval END
     WHERE id = $1 RETURNING id, premium_until`,
    [req.params.id, days],
  );
  if (!rows[0]) throw new HttpError(404, 'User tidak ditemukan');
  res.json(rows[0]);
}));

router.patch('/users/:id/badges', requireDashboardKey, wrap(async (req, res) => {
  const badges = [...new Set((Array.isArray(req.body.badges) ? req.body.badges : []).map(String))]
    .filter((badge) => /^[a-z0-9_-]{2,32}$/.test(badge)).slice(0, 20);
  const premium = await query('SELECT premium_until > now() AS active FROM users WHERE id = $1', [req.params.id]);
  if (!premium.rows[0]) throw new HttpError(404, 'User tidak ditemukan');
  if (!premium.rows[0].active && !badges.includes('admin')) throw new HttpError(403, 'Badge hanya untuk user premium');
  const { rows } = await query('UPDATE users SET badges = $2::jsonb WHERE id = $1 RETURNING id, badges', [
    req.params.id, JSON.stringify(badges),
  ]);
  res.json(rows[0]);
}));

module.exports = router;
