#!/usr/bin/env node
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const root = path.resolve(__dirname, '..');
const checks = [
  '@eclipse-glsp/vscode-integration',
  '@eclipse-glsp/vscode-integration-webview',
  '@eclipse-glsp/client',
  '@eclipse-glsp/protocol',
  'reflect-metadata'
];

function isInstalled(pkg) {
  const segments = pkg.split('/');
  const pkgPath = path.join(root, 'node_modules', ...segments, 'package.json');
  return fs.existsSync(pkgPath);
}

const missing = checks.filter(pkg => !isInstalled(pkg));

if (missing.length === 0) {
  process.exit(0);
}

console.log('[interlis] Installing missing npm dependencies:', missing.join(', '));
const result = spawnSync('npm', ['install'], { cwd: root, stdio: 'inherit' });
if (result.status !== 0) {
  console.error('[interlis] npm install failed. Please review the logs above.');
  process.exit(result.status ?? 1);
}
