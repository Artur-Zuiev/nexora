#!/usr/bin/env node
/**
 * Read-only local demo preflight. Never authenticates, mutates records or
 * prints credentials. No external hosts, dependencies or network services.
 */
import { pathToFileURL } from 'node:url';

export async function checkLocalService(url, expected, timeout = 4000) {
  const parsed = new URL(url);
  if (parsed.protocol !== 'http:' || !['127.0.0.1', 'localhost'].includes(parsed.hostname)) {
    throw new Error('Only local HTTP endpoints are permitted');
  }
  try {
    const response = await fetch(parsed, {
      method: 'GET', redirect: 'manual',
      signal: AbortSignal.timeout(timeout)
    });
    if (!response.ok) {
      return { ok: false, message: `HTTP ${response.status}` };
    }
    const body = await response.text();
    if (!expected(body)) {
      return { ok: false, message: 'Unexpected local service response' };
    }
    return { ok: true, message: `HTTP ${response.status}` };
  } catch (error) {
    return { ok: false, message: error?.cause?.code === 'ECONNREFUSED'
      ? 'Service is not running on this local port'
      : 'Could not reach the local service' };
  }
}

export async function checkMvp(backend = 'http://127.0.0.1:8080',
                               frontend = 'http://127.0.0.1:5173') {
  const checks = [
    ['Spring Boot CSRF endpoint', backend + '/api/session/csrf',
      value => { try {
        const csrf = JSON.parse(value);
        return typeof csrf.token === 'string' && csrf.token.length > 0
          && typeof csrf.headerName === 'string' && csrf.headerName.length > 0;
      } catch { return false; } }],
    ['React/Vite entry point', frontend + '/',
      value => /<html\b/i.test(value) && /id=["']root["']/.test(value)]
  ];
  const results = [];
  for (const [name, url, validate] of checks) {
    const result = await checkLocalService(url, validate);
    results.push({ name, ...result });
  }
  return results;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const results = await checkMvp();
  for (const result of results) {
    console.log(`${result.ok ? 'PASS' : 'FAIL'} ${result.name}: ${result.message}`);
  }
  if (results.every(result => result.ok)) {
    console.log('Local services respond. This is NOT a full security or E2E review.');
  } else {
    console.log('Start Docker, Spring Boot and Vite as described in README.md.');
    process.exitCode = 1;
  }
}
