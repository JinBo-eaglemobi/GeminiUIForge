const path = require('path');
const { rollupPack } = require('game-lib/lib/rollup-pack');

async function build() {
    const rootDir = path.resolve(__dirname, '..');
    const inputPath = path.resolve(rootDir, 'game/src/Main.ts');
    const outDir = path.resolve(rootDir, 'build');
    const tsconfigPath = path.resolve(rootDir, 'tsconfig.json');

    console.log(`Starting rollupPack build: ${inputPath} -> ${outDir}/main.min.js`);
    try {
        await rollupPack(inputPath, 'main', {
            minify: true,
            outDir: outDir,
            tsconfig: tsconfigPath,
            sourcemap: false
        });
        console.log('rollupPack build successfully completed.');
    } catch (e) {
        console.error('Build failed:', e);
        process.exit(1);
    }
}

build();
