const fs = require("fs");
const path = require("path");
const { minify } = require("terser");
const CleanCSS = require("clean-css");

const SRC = "src/main/webapp";
const DEST = "target/minified-assets";

function ensureDir(dir) {
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
}

function cleanDir(dir) {
    if (fs.existsSync(dir)) {
        fs.rmSync(dir, { recursive: true, force: true });
    }
}

async function processFile(srcPath, destPath) {
    const ext = path.extname(srcPath);

    try {
        // CSS → Procesar (Solo en carpeta /css/)
        if (ext === ".css" && srcPath.match(/[\\/]css[\\/]/)) {
            ensureDir(path.dirname(destPath));

            // Si el archivo ya es versión ".min", lo copiamos intacto para no desperdiciar CPU
            if (srcPath.endsWith(".min.css")) {
                fs.copyFileSync(srcPath, destPath);
            }
            // Si es un CSS tuyo (o el de iconos normales), lo minificamos
            else {
                const input = fs.readFileSync(srcPath, "utf8");
                const output = new CleanCSS().minify(input);
                if (output.errors.length) return console.error("CSS ERROR:", output.errors);
                fs.writeFileSync(destPath, output.styles);
            }
        }
        // JS → minificar y ofuscar (Solo en carpeta /scripts/)
        else if (ext === ".js" && srcPath.match(/[\\/]scripts[\\/]/)) {
            const input = fs.readFileSync(srcPath, "utf8");
            const result = await minify(input, {
                compress: true,
                mangle: true // true: ofusca los nombres de las variables
            });

            ensureDir(path.dirname(destPath));
            fs.writeFileSync(destPath, result.code);
        }
        // Los archivos que no cumplan esto (ej. imágenes o JS externos) serán ignorados por Node.
        // Maven se encargará de copiarlos desde src al WAR
    } catch (err) {
        console.error("ERROR processing:", srcPath, err.message);
    }
}

async function processDir(srcDir, destDir) {
    const files = fs.readdirSync(srcDir, { withFileTypes: true });

    for (const file of files) {
        const srcPath = path.join(srcDir, file.name);
        const destPath = path.join(destDir, file.name);

        if (file.isDirectory()) {
            await processDir(srcPath, destPath);
        } else {
            await processFile(srcPath, destPath);
        }
    }
}

(async () => {
    console.log("Processing files with Node and Terser...");
    cleanDir(DEST);
    await processDir(SRC, DEST);
    console.log("Minified files ready in", DEST);
})();