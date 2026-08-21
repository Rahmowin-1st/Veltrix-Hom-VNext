'use strict';

const http = require('http');
const crypto = require('crypto');
const { Pool } = require('pg');

const PORT = Number(process.env.PORT || 8080);
const DATABASE_URL = required('DATABASE_URL');
const S3_ACCESS_KEY = required('S3_ACCESS_KEY');
const S3_SECRET_KEY = required('S3_SECRET_KEY');
const S3_BUCKET = process.env.S3_BUCKET || 'veltrix-hom-vnext';
const MAX_BYTES = 55 * 1024 * 1024;

const pool = new Pool({
  connectionString: DATABASE_URL,
  ssl: DATABASE_URL.includes('sslmode=require') ? { rejectUnauthorized: false } : undefined,
  max: 5,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 8_000,
});

const initialized = pool.query(`
  CREATE TABLE IF NOT EXISTS veltrix_s3_object (
    bucket TEXT NOT NULL,
    object_key TEXT NOT NULL,
    body BYTEA NOT NULL,
    mime_type TEXT NOT NULL,
    sha256 TEXT NOT NULL,
    etag TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (bucket, object_key)
  )
`);

function required(name) {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required`);
  return value;
}

function sha256(data) {
  return crypto.createHash('sha256').update(data).digest('hex');
}

function hmac(key, data) {
  return crypto.createHmac('sha256', key).update(data).digest();
}

function signingKey(secret, date, region) {
  const kDate = hmac(Buffer.from(`AWS4${secret}`), Buffer.from(date));
  const kRegion = hmac(kDate, Buffer.from(region));
  const kService = hmac(kRegion, Buffer.from('s3'));
  return hmac(kService, Buffer.from('aws4_request'));
}

function encodeRfc3986(value) {
  return encodeURIComponent(value).replace(/[!'()*]/g, c => `%${c.charCodeAt(0).toString(16).toUpperCase()}`);
}

function constantTimeHexEquals(a, b) {
  if (!/^[0-9a-f]+$/i.test(a) || !/^[0-9a-f]+$/i.test(b) || a.length !== b.length) return false;
  return crypto.timingSafeEqual(Buffer.from(a, 'hex'), Buffer.from(b, 'hex'));
}

function canonicalHeaderValue(req, name) {
  if (name === 'host') return String(req.headers.host || '').trim();
  const value = req.headers[name];
  if (Array.isArray(value)) return value.join(',').trim();
  return String(value || '').trim();
}

function verifyHeaderSignature(req, rawPath, body) {
  const auth = String(req.headers.authorization || '');
  const match = auth.match(/^AWS4-HMAC-SHA256 Credential=([^/]+)\/(\d{8})\/([^/]+)\/s3\/aws4_request, SignedHeaders=([^,]+), Signature=([0-9a-f]{64})$/i);
  if (!match) return false;
  const [, accessKey, date, region, signedHeadersRaw, signature] = match;
  if (accessKey !== S3_ACCESS_KEY) return false;

  const signedHeaders = signedHeadersRaw.split(';').map(v => v.trim().toLowerCase()).filter(Boolean);
  if (!signedHeaders.length || !signedHeaders.includes('host')) return false;
  const canonicalHeaders = signedHeaders.map(name => `${name}:${canonicalHeaderValue(req, name)}\n`).join('');
  const payloadHash = String(req.headers['x-amz-content-sha256'] || sha256(body));
  if (payloadHash !== sha256(body)) return false;

  const canonicalRequest = `${req.method}\n${rawPath}\n\n${canonicalHeaders}\n${signedHeaders.join(';')}\n${payloadHash}`;
  const amzDate = String(req.headers['x-amz-date'] || '');
  if (!/^\d{8}T\d{6}Z$/.test(amzDate) || !amzDate.startsWith(date)) return false;
  const scope = `${date}/${region}/s3/aws4_request`;
  const toSign = `AWS4-HMAC-SHA256\n${amzDate}\n${scope}\n${sha256(Buffer.from(canonicalRequest))}`;
  const expected = crypto.createHmac('sha256', signingKey(S3_SECRET_KEY, date, region)).update(toSign).digest('hex');
  return constantTimeHexEquals(expected, signature.toLowerCase());
}

function parseAmzDate(value) {
  const m = /^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})Z$/.exec(value || '');
  if (!m) return NaN;
  return Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3]), Number(m[4]), Number(m[5]), Number(m[6]));
}

function verifyPresigned(req, rawPath, url) {
  const algorithm = url.searchParams.get('X-Amz-Algorithm');
  const credential = url.searchParams.get('X-Amz-Credential');
  const amzDate = url.searchParams.get('X-Amz-Date');
  const expires = Number(url.searchParams.get('X-Amz-Expires'));
  const signedHeaders = url.searchParams.get('X-Amz-SignedHeaders');
  const signature = url.searchParams.get('X-Amz-Signature');
  if (algorithm !== 'AWS4-HMAC-SHA256' || !credential || !amzDate || !signature || signedHeaders !== 'host') return false;

  const parts = credential.split('/');
  if (parts.length !== 5 || parts[0] !== S3_ACCESS_KEY || parts[3] !== 's3' || parts[4] !== 'aws4_request') return false;
  const [, date, region] = parts;
  if (!/^\d{8}$/.test(date) || !Number.isFinite(expires) || expires < 1 || expires > 3600) return false;
  const signedAt = parseAmzDate(amzDate);
  if (!Number.isFinite(signedAt) || Date.now() < signedAt - 5 * 60_000 || Date.now() > signedAt + expires * 1000 + 5 * 60_000) return false;

  const params = [];
  for (const [key, value] of url.searchParams.entries()) {
    if (key === 'X-Amz-Signature') continue;
    params.push([encodeRfc3986(key), encodeRfc3986(value)]);
  }
  params.sort((a, b) => a[0].localeCompare(b[0]) || a[1].localeCompare(b[1]));
  const canonicalQuery = params.map(([k, v]) => `${k}=${v}`).join('&');
  const host = String(req.headers.host || '').trim();
  const canonicalRequest = `GET\n${rawPath}\n${canonicalQuery}\nhost:${host}\n\nhost\nUNSIGNED-PAYLOAD`;
  const scope = `${date}/${region}/s3/aws4_request`;
  const toSign = `AWS4-HMAC-SHA256\n${amzDate}\n${scope}\n${sha256(Buffer.from(canonicalRequest))}`;
  const expected = crypto.createHmac('sha256', signingKey(S3_SECRET_KEY, date, region)).update(toSign).digest('hex');
  return constantTimeHexEquals(expected, signature.toLowerCase());
}

function parseObjectPath(rawPath) {
  const segments = rawPath.split('/').filter(Boolean);
  if (segments.length < 2) return null;
  let bucket;
  let key;
  try {
    bucket = decodeURIComponent(segments[0]);
    key = segments.slice(1).map(decodeURIComponent).join('/');
  } catch {
    return null;
  }
  if (bucket !== S3_BUCKET || !key) return null;
  return { bucket, key };
}

async function readBody(req) {
  const chunks = [];
  let total = 0;
  for await (const chunk of req) {
    total += chunk.length;
    if (total > MAX_BYTES) throw Object.assign(new Error('payload too large'), { statusCode: 413 });
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}

function json(res, status, value) {
  const body = Buffer.from(JSON.stringify(value));
  res.writeHead(status, { 'content-type': 'application/json', 'content-length': body.length });
  res.end(body);
}

async function handle(req, res) {
  await initialized;
  const rawUrl = req.url || '/';
  const rawPath = rawUrl.split('?')[0];

  if (rawPath === '/health') {
    await pool.query('SELECT 1');
    return json(res, 200, { ok: true, service: 'veltrix-postgres-s3' });
  }

  const object = parseObjectPath(rawPath);
  if (!object) return json(res, 404, { error: 'not_found' });

  const body = req.method === 'PUT' || req.method === 'DELETE' ? await readBody(req) : Buffer.alloc(0);
  const parsedUrl = new URL(rawUrl, `http://${req.headers.host || 'localhost'}`);
  const authorized = parsedUrl.searchParams.has('X-Amz-Signature')
    ? req.method === 'GET' && verifyPresigned(req, rawPath, parsedUrl)
    : verifyHeaderSignature(req, rawPath, body);
  if (!authorized) return json(res, 403, { error: 'signature_mismatch' });

  if (req.method === 'PUT') {
    const mimeType = String(req.headers['content-type'] || 'application/octet-stream');
    const declaredSha = String(req.headers['x-amz-meta-sha256'] || sha256(body));
    const actualSha = sha256(body);
    if (declaredSha !== actualSha) return json(res, 400, { error: 'sha256_mismatch' });
    const etag = crypto.createHash('md5').update(body).digest('hex');
    const result = await pool.query(
      `INSERT INTO veltrix_s3_object(bucket, object_key, body, mime_type, sha256, etag, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,now())
       ON CONFLICT (bucket, object_key) DO UPDATE SET body=excluded.body, mime_type=excluded.mime_type, sha256=excluded.sha256, etag=excluded.etag, updated_at=now()
       RETURNING extract(epoch from updated_at)::bigint AS version`,
      [object.bucket, object.key, body, mimeType, actualSha, etag],
    );
    res.writeHead(200, { etag: `"${etag}"`, 'x-amz-version-id': String(result.rows[0].version) });
    return res.end();
  }

  if (req.method === 'GET' || req.method === 'HEAD') {
    const result = await pool.query(
      'SELECT body, mime_type, sha256, etag FROM veltrix_s3_object WHERE bucket=$1 AND object_key=$2',
      [object.bucket, object.key],
    );
    if (!result.rowCount) return json(res, 404, { error: 'not_found' });
    const row = result.rows[0];
    const stored = Buffer.from(row.body);
    res.writeHead(200, {
      'content-type': row.mime_type,
      'content-length': stored.length,
      etag: `"${row.etag}"`,
      'x-amz-meta-sha256': row.sha256,
    });
    return req.method === 'HEAD' ? res.end() : res.end(stored);
  }

  if (req.method === 'DELETE') {
    await pool.query('DELETE FROM veltrix_s3_object WHERE bucket=$1 AND object_key=$2', [object.bucket, object.key]);
    res.writeHead(204);
    return res.end();
  }

  res.writeHead(405, { allow: 'PUT, GET, HEAD, DELETE' });
  res.end();
}

const server = http.createServer((req, res) => {
  handle(req, res).catch(err => {
    console.error(err);
    const status = Number(err.statusCode || 500);
    if (!res.headersSent) json(res, status, { error: status === 500 ? 'internal' : err.message });
    else res.destroy();
  });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`veltrix-postgres-s3 listening on ${PORT}`);
});

process.on('SIGTERM', async () => {
  server.close(async () => {
    await pool.end().catch(() => {});
    process.exit(0);
  });
});
