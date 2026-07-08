const fs = require('fs');
const zlib = require('zlib');

const texturePath = 'src/main/resources/assets/cavehorror/textures/entity/enderman_eyes_texture.png';

const buf = fs.readFileSync(texturePath);

// Parse PNG chunks
let pos = 8;
let width, height;
let idatChunks = [];
let otherChunks = [];

while (pos < buf.length) {
    const length = buf.readUInt32BE(pos);
    const type = buf.slice(pos + 4, pos + 8).toString('ascii');
    const data = buf.slice(pos + 8, pos + 8 + length);
    
    if (type === 'IDAT') {
        idatChunks.push(data);
    } else {
        otherChunks.push({ type, data: data.slice(0) });
        if (type === 'IHDR') {
            width = data.readUInt32BE(0);
            height = data.readUInt32BE(4);
        }
    }
    
    pos += 12 + length;
}

console.log(`Image: ${width}x${height}`);

// Decompress IDAT data
const compressed = Buffer.concat(idatChunks);
const decompressed = zlib.inflateSync(compressed);

const bpp = 4; // RGBA
const rowSize = 1 + width * bpp; // filter byte + pixels

// Analyze: find all non-transparent pixels
let nonTransparent = [];
for (let y = 0; y < height; y++) {
    const rowStart = y * rowSize + 1;
    for (let x = 0; x < width; x++) {
        const px = rowStart + x * 4;
        const a = decompressed[px + 3];
        if (a > 200) {
            const r = decompressed[px];
            const g = decompressed[px + 1];
            const b = decompressed[px + 2];
            nonTransparent.push({ x, y, r, g, b, a });
        }
    }
}

console.log(`Found ${nonTransparent.length} non-transparent pixels`);
nonTransparent.forEach(p => {
    console.log(`  (${p.x}, ${p.y}): R=${p.r} G=${p.g} B=${p.b} A=${p.a}`);
});

// Create new pixel data - everything transparent
const newData = Buffer.alloc(decompressed.length, 0);

// Set filter bytes to 0 (None)
for (let y = 0; y < height; y++) {
    newData[y * rowSize] = 0;
}

// Only keep pixels that are bright white (eyes)
const EYE_THRESHOLD = 180;
let kept = 0;

for (let y = 0; y < height; y++) {
    const rowStart = y * rowSize + 1;
    for (let x = 0; x < width; x++) {
        const px = rowStart + x * 4;
        const r = decompressed[px];
        const g = decompressed[px + 1];
        const b = decompressed[px + 2];
        const a = decompressed[px + 3];
        
        // Only keep bright white eye pixels
        if (r > EYE_THRESHOLD && g > EYE_THRESHOLD && b > EYE_THRESHOLD && a > 200) {
            newData[px] = 255;
            newData[px + 1] = 255;
            newData[px + 2] = 255;
            newData[px + 3] = 255;
            kept++;
        }
    }
}

console.log(`Kept ${kept} eye pixels`);

if (kept === 0) {
    console.log('WARNING: No eye pixels found! Keep all visible pixels instead.');
    for (let y = 0; y < height; y++) {
        const rowStart = y * rowSize + 1;
        for (let x = 0; x < width; x++) {
            const px = rowStart + x * 4;
            const a = decompressed[px + 3];
            if (a > 200) {
                newData[px] = decompressed[px];
                newData[px + 1] = decompressed[px + 1];
                newData[px + 2] = decompressed[px + 2];
                newData[px + 3] = 255;
                kept++;
            }
        }
    }
    console.log(`Kept ${kept} visible pixels as fallback`);
}

// Recompress
const newCompressed = zlib.deflateSync(newData);

// CRC32
function crc32(data) {
    let crc = 0xFFFFFFFF;
    for (let i = 0; i < data.length; i++) {
        crc ^= data[i];
        for (let j = 0; j < 8; j++)
            crc = (crc >>> 1) ^ (crc & 1 ? 0xEDB88320 : 0);
    }
    return (crc ^ 0xFFFFFFFF) >>> 0;
}

// Build output PNG
function makeChunk(type, data) {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length);
    const t = Buffer.from(type, 'ascii');
    const toCrc = Buffer.concat([t, data]);
    const c = Buffer.alloc(4);
    c.writeUInt32BE(crc32(toCrc));
    return Buffer.concat([len, t, data, c]);
}

const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
const output = Buffer.concat([
    signature,
    ...otherChunks.map(c => makeChunk(c.type, c.data)),
    makeChunk('IDAT', newCompressed),
    makeChunk('IEND', Buffer.alloc(0))
]);

fs.writeFileSync(texturePath, output);
console.log(`Written ${output.length} bytes to ${texturePath}`);
