const router = require('express').Router();
const multer = require('multer');
const { query } = require('../db');
const { maxMediaMb } = require('../config');
const { auth } = require('../middleware/auth');
const { HttpError, wrap } = require('../utils/errors');

const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: maxMediaMb * 1024 * 1024, files: 1 } });

// Upload file (field name: "file"). Hasilnya dipakai sebagai media_id saat kirim pesan / avatar.
router.post('/', auth, upload.single('file'), wrap(async (req, res) => {
  if (!req.file) throw new HttpError(400, 'File wajib diunggah (field "file")');
  const { originalname, mimetype, size, buffer } = req.file;
  const { rows } = await query(
    'INSERT INTO media (owner_id, mime, filename, size, data) VALUES ($1,$2,$3,$4,$5) RETURNING id, mime, filename, size',
    [req.userId, mimetype, Buffer.from(originalname, 'latin1').toString('utf8'), size, buffer]
  );
  res.status(201).json({ ...rows[0], url: `/api/media/${rows[0].id}` });
}));

// Unduh/tampilkan media. Boleh pakai ?token=JWT agar bisa dipakai di <img src>.
router.get('/:id', auth, wrap(async (req, res) => {
  const { rows } = await query(
    `SELECT m.mime, m.filename, m.data
     FROM media m
     WHERE m.id = $1
       AND (
         m.owner_id = $2
         OR EXISTS (SELECT 1 FROM users u WHERE u.avatar_media_id = m.id)
         OR EXISTS (
           SELECT 1
           FROM messages msg
           JOIN chat_members cm ON cm.chat_id = msg.chat_id
           WHERE msg.media_id = m.id AND cm.user_id = $2
         )
       )`,
    [req.params.id, req.userId]
  );
  if (!rows[0]) throw new HttpError(404, 'Media tidak ditemukan');
  res.set({
    'Content-Type': rows[0].mime,
    'Content-Disposition': `inline; filename*=UTF-8''${encodeURIComponent(rows[0].filename || 'file')}`,
    'Cache-Control': 'private, max-age=31536000, immutable',
    'X-Content-Type-Options': 'nosniff',
  });
  res.send(rows[0].data);
}));

module.exports = router;
