const { query } = require('../db');

// Push notification via Expo Push API (opsional, hanya untuk token Expo).
async function pushToUsers(userIds, { title, body, data }) {
  if (!userIds.length) return;
  const { rows } = await query('SELECT token FROM push_tokens WHERE user_id = ANY($1::uuid[])', [userIds]);
  const msgs = rows
    .filter((r) => /^Expo(nent)?PushToken\[/.test(r.token))
    .map((r) => ({ to: r.token, title, body, data, sound: 'default' }));
  for (let i = 0; i < msgs.length; i += 100) {
    await fetch('https://exp.host/--/api/v2/push/send', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(msgs.slice(i, i + 100)),
    });
  }
}

module.exports = { pushToUsers };
