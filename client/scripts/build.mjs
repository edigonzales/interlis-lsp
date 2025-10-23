import { build, context } from 'esbuild';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(__dirname, '..');
const tscExecutable = path.join(
  projectRoot,
  'node_modules',
  '.bin',
  process.platform === 'win32' ? 'tsc.cmd' : 'tsc'
);
const tscBaseArgs = ['--project', 'tsconfig.json', '--pretty', 'false'];

function runCommand(command, args, { cwd = projectRoot } = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { cwd, stdio: 'inherit' });

    child.once('error', error => reject(error));
    child.once('exit', code => {
      if (code === 0) {
        resolve();
      } else {
        reject(new Error(`${path.basename(command)} exited with code ${code ?? 'null'}`));
      }
    });
  });
}

async function runTypeCheckOnce() {
  await runCommand(tscExecutable, [...tscBaseArgs, '--noEmit']);
}

function startTypeCheckWatch() {
  const child = spawn(tscExecutable, [...tscBaseArgs, '--noEmit', '--watch'], {
    cwd: projectRoot,
    stdio: 'inherit'
  });

  let shuttingDown = false;

  child.on('error', error => {
    console.error(error);
    process.exit(1);
  });

  child.on('exit', code => {
    if (shuttingDown) {
      return;
    }

    if (code && code !== 0) {
      process.exit(code);
    }
  });

  return {
    async dispose() {
      if (shuttingDown) {
        return;
      }

      shuttingDown = true;

      await new Promise(resolve => {
        const handleExit = () => resolve();

        child.once('exit', handleExit);

        if (!child.kill('SIGINT')) {
          resolve();
        }
      });
    }
  };
}

const extensionOptions = {
  entryPoints: ['src/extension.ts'],
  bundle: true,
  platform: 'node',
  external: ['vscode', '@eclipse-glsp/*', '@vscode/codicons/*'],
  loader: {
    '.css': 'text',
    '.ttf': 'file',
    '.svg': 'dataurl'
  },
  outfile: 'dist/extension.js'
};

const glspOptions = {
  entryPoints: ['src/glsp/interlisGlspWebview.ts'],
  bundle: true,
  platform: 'browser',
  format: 'iife',
  external: ['@vscode/codicons/dist/codicon.css'],
  outfile: 'dist/interlis-glsp-webview.js'
};

const watch = process.argv.includes('--watch');

async function run() {
  if (watch) {
    const typeCheckWatcher = startTypeCheckWatch();
    const [extensionCtx, glspCtx] = await Promise.all([
      context(extensionOptions),
      context(glspOptions)
    ]);

    await Promise.all([extensionCtx.watch(), glspCtx.watch()]);

    console.log('Watching for changes...');

    const dispose = async () => {
      await Promise.all([
        extensionCtx.dispose(),
        glspCtx.dispose(),
        typeCheckWatcher.dispose()
      ]);
      process.exit(0);
    };

    process.on('SIGINT', dispose);
    process.on('SIGTERM', dispose);

    await new Promise(() => {});
  } else {
    await runTypeCheckOnce();
    await Promise.all([build(extensionOptions), build(glspOptions)]);
  }
}

run().catch(error => {
  console.error(error);
  process.exit(1);
});
