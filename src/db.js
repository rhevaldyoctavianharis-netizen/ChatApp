const { Pool } = require('pg');

const url = process.env.DATABASE_URL || 'postgres://chat:chat@localhost:5432/chatapp';
const isLocal = /localhost|127\.0\.0\.1/.test(url);
const ssl = process.env.DATABASE_SSL === 'false' || isLocal
  ? false
  : {
      rejectUnauthorized: true,
      ...(process.env.DATABASE_SSL_CA ? { ca: process.env.DATABASE_SSL_CA } : {}),
    };

const pool = new Pool({
  connectionString: url,
  ssl,
  max: Number(process.env.PG_POOL_MAX || 10),
});

pool.on('error', (err) => console.error('[pg] idle client error', err.message));

module.exports = { pool, query: (text, params) => pool.query(text, params) };
