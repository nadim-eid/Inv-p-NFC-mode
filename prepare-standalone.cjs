const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const source = path.join(root, 'Chemical-Stock-Manager-HSE.html');
if (!fs.existsSync(source)) throw Error('Chemical-Stock-Manager-HSE.html absent à la racine.');
fs.mkdirSync(path.join(root, 'www'), { recursive: true });
fs.copyFileSync(source, path.join(root, 'www/index.html'));
console.log('Page autonome préparée (NFC local Android).');
