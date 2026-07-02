import * as fs from "fs";
import * as path from "path";
import { fileURLToPath } from "url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const logsDir = path.resolve(__dirname, "logs");

if (!fs.existsSync(logsDir)) {
    fs.mkdirSync(logsDir, { recursive: true });
}

const logFileName = `${new Date().toISOString().replace(/[:.]/g, "-").slice(0, 19)}.log`;
const logFilePath = path.join(logsDir, logFileName);
const logStream = fs.createWriteStream(logFilePath, { flags: "a" });

function formatLine(level: string, message: string): string {
    return `[${new Date().toISOString()}] [${level}] ${message}`;
}

function writeToFile(line: string) {
    logStream.write(line + "\n");
}

export const log = {
    info: (message: string) => {
        const line = formatLine("INFO", message);
        console.log(line);
        writeToFile(line);
    },
    warn: (message: string) => {
        const line = formatLine("WARN", message);
        console.warn(line);
        writeToFile(line);
    },
    error: (message: string) => {
        const line = formatLine("ERROR", message);
        console.error(line);
        writeToFile(line);
    },
    raw: (message: string) => {
        const line = formatLine("INFO", message);
        process.stdout.write(line + "\n");
        writeToFile(line);
    }
};
