const { test } = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const Module = require('node:module');
const { buildSync } = require('esbuild');

// Exercise the production helper without VS Code or an extra test dependency.
const entry = path.resolve(__dirname, '../src/templateDownload.ts');
const compiled = buildSync({ entryPoints: [entry], bundle: true, platform: 'node', format: 'cjs', write: false });
const loaded = new Module(entry, module);
loaded._compile(compiled.outputFiles[0].text, entry);
const { fetchTemplateContent, describeTemplateFailure, TemplateDownloadError, TEMPLATE_FETCH_TIMEOUT_MS } = loaded.exports;
const url = 'https://repo.example/template.ili';

test('successful template download preserves content', async () => {
  assert.equal(await fetchTemplateContent(url, async () => new Response('INTERLIS 2.4;\n')), 'INTERLIS 2.4;\n');
});

for (const [code, expected] of [
  ['ENOTFOUND', /server name could not be resolved/],
  ['EAI_AGAIN', /server name could not be resolved/],
  ['ECONNREFUSED', /connection could not be established/],
  ['ECONNRESET', /connection could not be established/],
  ['ETIMEDOUT', /request timed out/],
  ['CERT_HAS_EXPIRED', /server certificate/],
  ['ERR_TLS_CERT_ALTNAME_INVALID', /server certificate/],
]) {
  test(`explains nested ${code} and preserves the technical cause`, async () => {
    const cause = Object.assign(new Error('technical detail'), { code });
    const original = new TypeError('fetch failed', { cause });
    await assert.rejects(fetchTemplateContent(url, async () => { throw original; }), error => {
      assert.ok(error instanceof TemplateDownloadError);
      assert.match(error.message, expected);
      assert.ok(error.message.includes(url));
      assert.equal(error.technicalCause, original);
      assert.doesNotMatch(error.message, /offline|no internet/i);
      return true;
    });
  });
}

for (const status of [404, 500]) {
  test(`reports actual HTTP ${status}`, async () => {
    await assert.rejects(fetchTemplateContent(url, async () => new Response('', { status })), new RegExp(`HTTP ${status}`));
  });
}

test('timeout aborts the request and clears the timer', async context => {
  context.mock.timers.enable({ apis: ['setTimeout'] });
  let aborted = false;
  const request = fetchTemplateContent(url, async (_url, options) => new Promise((_resolve, reject) => {
    options.signal.addEventListener('abort', () => {
      aborted = true;
      reject(new DOMException('aborted', 'AbortError'));
    });
  }));
  context.mock.timers.tick(TEMPLATE_FETCH_TIMEOUT_MS);
  await assert.rejects(request, /timed out after 3 seconds/);
  assert.equal(aborted, true);
});

test('unknown failures retain details without guessing a network cause', async () => {
  assert.match(describeTemplateFailure(new Error('unclassified problem'), url), /unclassified problem/);
  assert.doesNotMatch(describeTemplateFailure(new Error('unclassified problem'), url), /DNS|VPN|certificate|timed out/);
  await assert.rejects(fetchTemplateContent(url, async () => new Response('   ')), /empty response/);
});

test('successful download clears its timeout', async context => {
  context.mock.timers.enable({ apis: ['setTimeout'] });
  let signal;
  await fetchTemplateContent(url, async (_url, options) => {
    signal = options.signal;
    return new Response('source');
  });
  context.mock.timers.tick(TEMPLATE_FETCH_TIMEOUT_MS);
  assert.equal(signal.aborted, false);
});
