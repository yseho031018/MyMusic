import { build, Platform } from 'electron-builder';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
// Editors that index the workspace hold the unpacked app.asar open, which breaks the next build.
const work = fs.mkdtempSync(path.join(os.tmpdir(), 'mymusic-build-'));
const release = path.join(root, 'release');
const config = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8')).build;

await build({
  projectDir: root,
  targets: Platform.WINDOWS.createTarget('nsis'),
  publish: 'never',
  config: { ...config, directories: { ...config.directories, output: work } },
});

fs.mkdirSync(release, { recursive: true });
for (const name of fs.readdirSync(work)) {
  if (name.endsWith('.exe') || name.endsWith('.blockmap') || name === 'latest.yml') {
    fs.copyFileSync(path.join(work, name), path.join(release, name));
    console.log(`release/${name}`);
  }
}
fs.rmSync(work, { recursive: true, force: true });
