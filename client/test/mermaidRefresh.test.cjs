const { test } = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const Module = require('node:module');
const { buildSync } = require('esbuild');
const entry = path.resolve(__dirname, '../src/mermaidRefresh.ts');
const compiled = buildSync({ entryPoints: [entry], bundle: true, platform: 'node', format: 'cjs', write: false });
const loaded = new Module(entry, module);
loaded._compile(compiled.outputFiles[0].text, entry);
const { MermaidRefreshController } = loaded.exports;
const tick = () => new Promise(resolve => setImmediate(resolve));
const diagram = status => ({ source: 'classDiagram\nclass Saved', hideEmptyMembersBox: false, status });
function harness() {
  const requests = [], messages = [], errors = [];
  const controller = new MermaidRefreshController(uri => new Promise((resolve, reject) => requests.push({ uri, resolve, reject })),
    message => messages.push(message), error => errors.push(error));
  const open = (uri='file:///a.ili', kind='mermaid') => {
    const id = controller.beginOpen(uri, kind);
    assert.equal(controller.completeOpen(id, true), true);
    controller.webviewReady(String(id));
    return id;
  };
  return { controller, requests, messages, errors, open, updates: () => messages.filter(m => m.type === 'mermaid:update') };
}

test('does not open diagrams and waits for the matching webview readiness', async () => {
  const h = harness();
  h.controller.refresh();h.controller.refresh('file:///a.ili');
  assert.equal(h.requests.length, 0);
  const id = h.controller.beginOpen('file:///a.ili', 'mermaid');
  h.controller.refresh('file:///a.ili');
  h.controller.completeOpen(id, true);
  h.controller.webviewReady('obsolete');
  assert.equal(h.requests.length, 0);
  h.controller.webviewReady(String(id));
  assert.equal(h.requests.length, 1);
  h.requests[0].resolve(diagram('current'));await tick();
  assert.equal(h.updates().length, 1);
  h.controller.refresh('file:///other.ili');
  assert.equal(h.requests.length, 1);
});

test('coalesces rapid saves/settings and never publishes outdated results', async () => {
  const h = harness();h.open();
  h.controller.refresh();h.controller.refresh('file:///a.ili');h.controller.refresh();
  assert.equal(h.requests.length, 1);
  h.requests[0].resolve(diagram('current'));await tick();
  assert.equal(h.updates().length, 0);
  assert.equal(h.requests.length, 2);
  h.requests[1].resolve(diagram('last-valid'));await tick();
  assert.equal(h.updates().length, 1);
  assert.equal(h.updates()[0].diagram.status, 'last-valid');
  h.controller.refresh();
  assert.equal(h.requests.length, 3);
});

test('closing or switching to PlantUML invalidates outstanding Mermaid responses', async () => {
  for (const action of ['close', 'plantuml']) {
    const h = harness();h.open();
    if (action === 'close') h.controller.close(); else h.open('file:///b.ili','plantuml');
    h.requests[0].resolve(diagram('current'));await tick();
    h.controller.refresh();
    assert.equal(h.updates().length, 0);
    assert.equal(h.requests.length, 1);
  }
});

test('source changes and overlapping manual opens are isolated', async () => {
  const h = harness();h.open();
  const older = h.controller.beginOpen('file:///b.ili','mermaid');
  const newer = h.controller.beginOpen('file:///c.ili','mermaid');
  assert.equal(h.controller.completeOpen(older,true),false);
  assert.equal(h.controller.completeOpen(newer,true),true);
  h.controller.webviewReady(String(newer));
  h.requests[0].resolve(diagram('current'));await tick();
  assert.equal(h.requests.length,2);
  assert.equal(h.requests[1].uri,'file:///c.ili');
  assert.equal(h.updates().length,0);
  h.requests[1].resolve(diagram('current'));await tick();
  assert.equal(h.updates()[0].viewId,String(newer));
});

test('failed manual open resumes the previous view; automatic errors are inline', async () => {
  const h = harness();h.open();h.requests[0].resolve(diagram('current'));await tick();
  const id = h.controller.beginOpen('file:///b.ili','plantuml');
  h.controller.completeOpen(id,false);
  assert.equal(h.requests[1].uri,'file:///a.ili');
  h.requests[1].reject(new Error('connection lost'));await tick();
  assert.equal(h.errors.length,1);
  assert.equal(h.messages.at(-1).type,'mermaid:error');
  h.controller.refresh();h.requests[2].resolve(diagram('current'));await tick();
  assert.equal(h.updates().length,2);
});

test('empty and unavailable states are passed through; pending work survives a close/reopen', async () => {
  const h = harness();h.open();h.controller.close();const id=h.open('file:///b.ili');
  h.requests[0].resolve(diagram('current'));await tick();
  h.requests[1].resolve({source:null,hideEmptyMembersBox:true,status:'empty'});await tick();
  assert.equal(h.updates()[0].viewId,String(id));assert.equal(h.updates()[0].diagram.status,'empty');
  h.controller.refresh();h.requests[2].resolve({source:null,hideEmptyMembersBox:true,status:'unavailable'});await tick();
  assert.equal(h.updates()[1].diagram.status,'unavailable');
});
