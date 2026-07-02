import { createHash } from 'crypto';

function encodeGeoHash(lat: number, lon: number): string {
    const latSnapped = Math.min(399, Math.max(0, Math.floor((lat + 90) / 0.45)));
    const lonSnapped = Math.min(799, Math.max(0, Math.floor((lon + 180) / 0.45)));
    const packed = (latSnapped << 12) | lonSnapped;
    const buf = Buffer.alloc(3);
    buf[0] = (packed >> 16) & 0xff;
    buf[1] = (packed >> 8) & 0xff;
    buf[2] = packed & 0xff;
    return buf.toString("hex");
}

const lat = parseFloat(process.argv[2]);
const lon = parseFloat(process.argv[3]);

if (isNaN(lat) || isNaN(lon)) {
    console.log("Usage: npx tsx test/geo_test.ts <lat> <lon>");
    process.exit(1);
}

const hash = encodeGeoHash(lat, lon);
console.log(`Input: lat=${lat}, lon=${lon}`);
console.log(`Resulting 3-byte Geohash: ${hash}`);
