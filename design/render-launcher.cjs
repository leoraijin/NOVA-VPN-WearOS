// Legacy installer/launcher PNG fallback. Adaptive icon uses matching native vectors.
const path = require('node:path');
const fs = require('node:fs');
const sharp = require('sharp');
async function main() {
  for (const [density, size] of Object.entries({mdpi:48, hdpi:72, xhdpi:96, xxhdpi:144, xxxhdpi:192})) {
    const folder = path.join(__dirname, '../app/src/main/res', `mipmap-${density}`);
    fs.mkdirSync(folder, {recursive:true});
    const image = await sharp(path.join(__dirname, 'nova-launcher.svg')).resize(size, size).png().toBuffer();
    fs.writeFileSync(path.join(folder, 'ic_launcher.png'), image);
    fs.writeFileSync(path.join(folder, 'nova_installer.png'), image);
  }
}
main().catch(error => { console.error(error); process.exitCode=1; });
