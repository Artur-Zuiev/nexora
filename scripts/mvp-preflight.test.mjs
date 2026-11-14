import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { once } from 'node:events';
import { checkLocalService, checkMvp } from './mvp-preflight.mjs';

const backend = createServer((req, res) => {
  res.setHeader('Content-Type', 'application/json');
  if (req.url === '/api/session/csrf') {
    res.end(JSON.stringify({ token: 'local-test-token', headerName: 'X-CSRF-TOKEN' }));
  } else { res.statusCode = 404; res.end('{}'); }
});
const frontend = createServer((_req, res) => {
  res.setHeader('Content-Type', 'text/html');
  res.end('<!doctype html><html><body><div id="root"></div></body></html>');
});
let backendUrl;
let frontendUrl;

before(async () => {
  backend.listen(0, '127.0.0.1');
  frontend.listen(0, '127.0.0.1');
  await Promise.all([once(backend, 'listening'), once(frontend, 'listening')]);
  backendUrl = `http://127.0.0.1:${backend.address().port}`;
  frontendUrl = `http://127.0.0.1:${frontend.address().port}`;
});
after(async () => {
  await Promise.all([
    new Promise(resolve => backend.close(resolve)),
    new Promise(resolve => frontend.close(resolve))
  ]);
});

test('accepts only loopback HTTP endpoints', async () => {
  await assert.rejects(checkLocalService('https://example.com/', () => true), /Only local/);
  await assert.rejects(checkLocalService('http://example.com/', () => true), /Only local/);
  await assert.rejects(checkLocalService('http://0.0.0.0:3000/', () => true), /Only local/);
});

test('validates both local demo services without logging credentials', async () => {
  const results = await checkMvp(backendUrl, frontendUrl);
  assert.equal(results.length, 2);
  assert.ok(results.every(result => result.ok));
});

test('detects unexpected responses and closed ports', async () => {
  const failed = await checkLocalService(backendUrl + '/missing', () => true);
  assert.equal(failed.ok, false);
  const missing = await checkLocalService('http://127.0.0.1:1/', () => true, 500);
  assert.equal(missing.ok, false);
});
