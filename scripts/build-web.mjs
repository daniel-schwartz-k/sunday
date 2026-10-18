import { cpSync, rmSync, mkdirSync, existsSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const rootDir = resolve(__dirname, '..');
const outDir = resolve(rootDir, 'www');

// Clean target directory
if (existsSync(outDir)) {
  rmSync(outDir, { recursive: true, force: true });
}
mkdirSync(outDir, { recursive: true });

// Whitelist of files and directories to bundle into the native Android webDir.
// Keeps out node_modules, android, .git, .github, .claude, scripts, etc.
const assetsToCopy = [
  'index.html',
  'app.js',
  'styles.css',
  'manifest.webmanifest',
  'service-worker.js',
  'models',
  'vendor',
  'icons',
  'banners'
];

for (const asset of assetsToCopy) {
  const src = resolve(rootDir, asset);
  const dest = resolve(outDir, asset);
  if (!existsSync(src)) {
    console.warn(`[build:web] Warning: Source asset "${asset}" not found at ${src}`);
    continue;
  }
  cpSync(src, dest, { recursive: true });
}

console.log(`[build:web] Bundled ${assetsToCopy.length} assets into ${outDir}`);
