// 用真实 pathData 渲染一张「按钮长什么样」的预览图（sharp/librsvg 光栅化）
//
// 用法（可选，只有想更新 docs/button-preview.png 时才需要）：
//   npm install sharp @img/sharp-win32-x64      # 或用对应平台的包
//   node tools/render-preview.js                # 输出 _preview/button-preview.png
//
const fs = require('fs');
const path = require('path');
const sharp = require('sharp');

const DRAWABLE = path.join(__dirname, '..', 'app', 'src', 'main', 'res', 'drawable',
  'ic_gpay_wordmark.xml');
const xml = fs.readFileSync(DRAWABLE, 'utf8');

const paths = [...xml.matchAll(/android:fillColor="([^"]+)"\s+android:pathData="([^"]+)"/g)]
  .map((m) => `<path fill="${m[1]}" d="${m[2]}"/>`);

const W = 1080;
const H = 760;

// 药丸：108x56dp，这里按 3x 渲染
const pw = 324;
const ph = 168;
const px = 690;
const py = 500;

const logoH = ph * 0.58;
const logoW = logoH * 80 / 38.1;
const lx = px + (pw - logoW) / 2;
const ly = py + (ph - logoH) / 2;
const s = logoW / 80;

const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#1b2019"/>
      <stop offset="0.5" stop-color="#232a20"/>
      <stop offset="1" stop-color="#101413"/>
    </linearGradient>
    <filter id="shadow" x="-30%" y="-30%" width="160%" height="180%">
      <feDropShadow dx="0" dy="8" stdDeviation="12" flood-color="#000" flood-opacity="0.45"/>
    </filter>
  </defs>
  <rect width="${W}" height="${H}" fill="url(#bg)"/>
  <rect x="0" y="0" width="${W}" height="150" fill="#000000" opacity="0.28"/>
  <rect x="0" y="150" width="${W}" height="1" fill="#ffffff" opacity="0.06"/>
  <g filter="url(#shadow)">
    <rect x="${px}" y="${py}" width="${pw}" height="${ph}" rx="${ph / 2}" fill="#ffffff"/>
  </g>
  <rect x="${px + 1}" y="${py + 1}" width="${pw - 2}" height="${ph - 2}" rx="${ph / 2 - 1}"
        fill="none" stroke="#DADCE0" stroke-width="2"/>
  <g transform="translate(${lx}, ${ly}) scale(${s})">
    ${paths.join('\n    ')}
  </g>
</svg>`;

fs.writeFileSync(path.join(__dirname, 'button-preview.svg'), svg);

sharp(Buffer.from(svg)).png().toFile(path.join(__dirname, 'button-preview.png'))
  .then((info) => console.log('ok', info.width + 'x' + info.height))
  .catch((e) => { console.error('ERR', e); process.exit(1); });
