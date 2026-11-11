const fs = require('fs');
const path = require('path');

const rootDir = __dirname;
const buildDir = path.resolve(rootDir, 'build');
const gameLibDist = path.resolve(rootDir, 'node_modules/game-lib/dist');

if (!fs.existsSync(buildDir)) {
    fs.mkdirSync(buildDir, { recursive: true });
}

if (fs.existsSync(gameLibDist)) {
    const files = fs.readdirSync(gameLibDist);
    for (const file of files) {
        if (file.endsWith('.min.js')) {
            const src = path.resolve(gameLibDist, file);
            const dest = path.resolve(buildDir, file);
            fs.copyFileSync(src, dest);
            console.log(`Copied: ${file} -> build/`);
        }
    }
} else {
    console.warn(`[WARN] gameLibDist not found: ${gameLibDist}`);
}
console.log('copyFiles completed.');
