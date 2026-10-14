// Generate the static tile picker preview; this is not a device screenshot.
const path = require('node:path');
const sharp = require('sharp');
const input = path.join(__dirname, 'nova-tile-preview.svg');
const output = path.join(__dirname, '../app/src/main/res/drawable-nodpi/nova_tile_preview.png');
sharp(input).png().toFile(output).then(() => console.log(output)).catch(error => { console.error(error); process.exitCode = 1; });
