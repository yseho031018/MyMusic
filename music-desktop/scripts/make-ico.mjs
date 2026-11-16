import pngToIco from 'png-to-ico';
import { readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const png = await readFile(path.join(root, 'assets', 'icon.png'));
const ico = await pngToIco(png);
await writeFile(path.join(root, 'assets', 'icon.ico'), ico);
