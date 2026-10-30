class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

function normalizePhone(input) {
  const p = String(input || '').replace(/[\s\-()]/g, '');
  return /^\+[1-9]\d{7,14}$/.test(p) ? p : null;
}

const toUser = (r) => ({
  id: r.id,
  phone: r.phone,
  display_name: r.display_name,
  about: r.about,
  avatar_media_id: r.avatar_media_id,
  last_seen: r.last_seen,
  created_at: r.created_at,
});

module.exports = { HttpError, wrap, normalizePhone, toUser };
