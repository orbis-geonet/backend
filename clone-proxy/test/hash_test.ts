import { createHash } from "crypto";



function hashKeyFull(value: string | undefined): string {
    if (!value) return Buffer.alloc(8).toString("hex");
    return createHash("sha256").update(value).digest().subarray(0, 8).toString("hex");
}

function hashShort(value: string | undefined): string {
    if (!value) return Buffer.alloc(4).toString("hex");
    return createHash("sha256").update(value).digest().subarray(0, 4).toString("hex");
}

function encodeGeoHash(lat: number, lon: number): string {
    const buf = Buffer.alloc(3);
    const latSnapped = Math.min(399, Math.max(0, Math.floor((lat + 90) / 0.45)));
    const lonSnapped = Math.min(799, Math.max(0, Math.floor((lon + 180) / 0.45)));

    const packed = (latSnapped << 12) | lonSnapped;
    buf[0] = (packed >> 16) & 0xff;
    buf[1] = (packed >> 8) & 0xff;
    buf[2] = packed & 0xff;
    return buf.toString("hex");
}

function encodeTimestamp(dateStr?: string): string {
    const buf = Buffer.alloc(4);
    const ts = dateStr ? new Date(dateStr).getTime() : Date.now();
    const epoch = Math.floor(ts / 1000);
    buf.writeUInt32LE(epoch >>> 0, 0);
    return buf.toString("hex");
}


const args = process.argv.slice(2);
if (args.length === 0) {
    console.log(`Usage: npx tsx test/hash_test.ts <string_to_hash> [lat] [lon]`);
    console.log(`Example: npx tsx test/hash_test.ts "67a3bbd2c5512302ae483668" 37.42 -122.08`);

    // Default Run with example
    const testVal = "67a3bbd2c5512302ae483668";
    console.log(`\n--- Example Run for Group ID: ${testVal} ---`);
    console.log(`KeyHash (8B):       ${hashKeyFull(testVal)}`);
    console.log(`ShortHash (4B):     ${hashShort(testVal)}`);
    console.log(`GeoHash (3B):       ${encodeGeoHash(37.4219, -122.084)} (lat: 37.42, lon: -122.08)`);
    console.log(`Timestamp (4B):    ${encodeTimestamp()}`);
} else {
    const val = args[0];
    const lat = parseFloat(args[1] || "0");
    const lon = parseFloat(args[2] || "0");

    console.log(`\nResults for input: "${val}"`);
    console.log(`-----------------------------------`);
    console.log(`KeyHash (8B):       ${hashKeyFull(val)}`);
    console.log(`ShortHash (4B):     ${hashShort(val)}`);
    if (args[1] && args[2]) {
        console.log(`GeoHash (3B):       ${encodeGeoHash(lat, lon)}`);
    }
    console.log(`Timestamp (4B):    ${encodeTimestamp()}`);
}
