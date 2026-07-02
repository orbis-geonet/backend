"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.modifyVideo = exports.modifyImage = exports.supportedImageContentTypeMap = exports.supportedContentTypes = exports.convertType = exports.resize = void 0;
const os = require("os");
const sharp = require("sharp");
const path = require("path");
const fs = require("fs");
const uuidv4_1 = require("uuidv4");
const config_1 = require("./config");
const logs = require("./logs");
const admin = require("firebase-admin");
const ffmpeg = require("@ffmpeg-installer/ffmpeg");
const firebase_functions_1 = require("firebase-functions");
const spawn = require('child-process-promise').spawn;
function resize(file, size) {
    let height, width;
    if (size.indexOf(",") !== -1) {
        [width, height] = size.split(",");
    }
    else if (size.indexOf("x") !== -1) {
        [width, height] = size.split("x");
    }
    else {
        throw new Error("height and width are not delimited by a ',' or a 'x'");
    }
    return sharp(file)
        .rotate()
        .resize(parseInt(width, 10), parseInt(height, 10), {
        fit: "inside",
        withoutEnlargement: false,
    })
        .toBuffer();
}
exports.resize = resize;
function convertType(buffer, format) {
    if (format === "jpg" || format === "jpeg") {
        return sharp(buffer)
            .jpeg()
            .toBuffer();
    }
    if (format === "png") {
        return sharp(buffer)
            .png()
            .toBuffer();
    }
    if (format === "webp") {
        return sharp(buffer)
            .webp()
            .toBuffer();
    }
    if (format === "tiff") {
        return sharp(buffer)
            .tiff()
            .toBuffer();
    }
    return buffer;
}
exports.convertType = convertType;
/**
 * Supported file types
 */
exports.supportedContentTypes = [
    "image/jpeg",
    "image/png",
    "image/tiff",
    "image/webp",
];
exports.supportedImageContentTypeMap = {
    jpg: "image/jpeg",
    jpeg: "image/jpeg",
    png: "image/png",
    tiff: "image/tiff",
    webp: "image/webp",
};
const supportedExtensions = Object.keys(exports.supportedImageContentTypeMap).map((type) => `.${type}`);
function normalizeForRtDb(modifiedFilePath) {
    let noExtenseion = modifiedFilePath.replace(/\.\w+?/, "");
    let parts = noExtenseion.split("_");
    let size = (parts.length < 2) ? "nosize" : parts.pop().split("x")[0];
    let name = parts.join("_").replace(/[. #$\[\]]/, "_");
    return `${name}/${size}`;
}
exports.modifyImage = async ({ bucket, originalFile, fileDir, fileNameWithoutExtension, fileExtension, contentType, size, objectMetadata, format, }) => {
    const shouldFormatImage = format !== "false";
    const imageContentType = shouldFormatImage
        ? exports.supportedImageContentTypeMap[format]
        : contentType;
    const modifiedExtensionName = fileExtension && shouldFormatImage ? `.${format}` : fileExtension;
    let modifiedFileName;
    if (supportedExtensions.includes(fileExtension.toLowerCase())) {
        modifiedFileName = `${fileNameWithoutExtension}_${size}${modifiedExtensionName}`;
    }
    else {
        // Fixes https://github.com/firebase/extensions/issues/476
        modifiedFileName = `${fileNameWithoutExtension}${fileExtension}_${size}`;
    }
    // Path where modified image will be uploaded to in Storage.
    const modifiedFilePath = path.normalize(config_1.default.resizedImagesPath
        ? path.join(fileDir, config_1.default.resizedImagesPath, modifiedFileName)
        : path.join(fileDir, modifiedFileName));
    let modifiedFile;
    try {
        modifiedFile = path.join(os.tmpdir(), modifiedFileName);
        // Cloud Storage files.
        const metadata = {
            contentDisposition: objectMetadata.contentDisposition,
            contentEncoding: objectMetadata.contentEncoding,
            contentLanguage: objectMetadata.contentLanguage,
            contentType: imageContentType,
            metadata: objectMetadata.metadata || {},
        };
        metadata.metadata.resizedImage = true;
        if (config_1.default.cacheControlHeader) {
            metadata.cacheControl = config_1.default.cacheControlHeader;
        }
        else {
            metadata.cacheControl = objectMetadata.cacheControl;
        }
        // If the original image has a download token, add a
        // new token to the image being resized #323
        if (metadata.metadata.firebaseStorageDownloadTokens) {
            metadata.metadata.firebaseStorageDownloadTokens = uuidv4_1.uuid();
        }
        // Generate a resized image buffer using Sharp.
        logs.imageResizing(modifiedFile, size);
        let modifiedImageBuffer = await resize(originalFile, size);
        logs.imageResized(modifiedFile);
        // Generate a converted image type buffer using Sharp.
        if (shouldFormatImage) {
            logs.imageConverting(fileExtension, format);
            modifiedImageBuffer = await convertType(modifiedImageBuffer, format);
            logs.imageConverted(format);
        }
        // Generate a image file using Sharp.
        await sharp(modifiedImageBuffer).toFile(modifiedFile);
        // Uploading the modified image.
        logs.imageUploading(modifiedFilePath);
        await bucket.upload(modifiedFile, {
            destination: modifiedFilePath,
            metadata,
        });
        logs.imageUploaded(modifiedFile);
        logs.notifyingCompleted("/images/" + modifiedFilePath);
        let normalizedRtDbPath = normalizeForRtDb(modifiedFilePath);
        await admin.database().ref("/images/" + normalizedRtDbPath).set({
            generated: true
        });
        logs.notifiedCompleted("/images/" + normalizedRtDbPath);
        return { size, success: true };
    }
    catch (err) {
        logs.error(err);
        return { size, success: false };
    }
    finally {
        try {
            // Make sure the local resized file is cleaned up to free up disk space.
            if (modifiedFile) {
                logs.tempResizedFileDeleting(modifiedFilePath);
                fs.unlinkSync(modifiedFile);
                logs.tempResizedFileDeleted(modifiedFilePath);
            }
        }
        catch (err) {
            logs.errorDeleting(err);
        }
    }
};
exports.modifyVideo = async ({ bucket, originalFile, fileDir, fileNameWithoutExtension, fileExtension, contentType, size, objectMetadata, format, }) => {
    const modifiedExtensionName = '.jpg';
    let modifiedFileName = `${fileNameWithoutExtension}_${size}${modifiedExtensionName}`;
    // Path where modified image will be uploaded to in Storage.
    const modifiedFilePath = path.normalize(config_1.default.resizedImagesPath
        ? path.join(fileDir, config_1.default.resizedImagesPath, modifiedFileName)
        : path.join(fileDir, modifiedFileName));
    let modifiedFile;
    try {
        modifiedFile = path.join(os.tmpdir(), modifiedFileName);
        // Cloud Storage files.
        const metadata = {
            contentDisposition: objectMetadata.contentDisposition,
            contentEncoding: objectMetadata.contentEncoding,
            contentLanguage: objectMetadata.contentLanguage,
            contentType: exports.supportedImageContentTypeMap.jpg,
            metadata: objectMetadata.metadata || {},
        };
        metadata.metadata.resizedImage = true;
        if (config_1.default.cacheControlHeader) {
            metadata.cacheControl = config_1.default.cacheControlHeader;
        }
        else {
            metadata.cacheControl = objectMetadata.cacheControl;
        }
        // If the original image has a download token, add a
        // new token to the image being resized #323
        if (metadata.metadata.firebaseStorageDownloadTokens) {
            metadata.metadata.firebaseStorageDownloadTokens = uuidv4_1.uuid();
        }
        // Generate a resized image buffer using Sharp.
        logs.imageResizing(modifiedFile, size);
        let converted = await convertFfmpeg(originalFile, modifiedFile, size);
        logs.imageResized(modifiedFile);
        logs.imageUploading(modifiedFilePath);
        await bucket.upload(modifiedFile, {
            destination: modifiedFilePath,
            metadata,
        });
        logs.imageUploaded(modifiedFile);
        logs.notifyingCompleted("/images/" + modifiedFilePath);
        let normalizedRtDbPath = normalizeForRtDb(modifiedFilePath);
        await admin.database().ref("/images/" + normalizedRtDbPath).set({
            generated: true
        });
        logs.notifiedCompleted("/images/" + normalizedRtDbPath);
        return { size, success: true };
    }
    catch (err) {
        logs.error(err);
        return { size, success: false };
    }
    finally {
        try {
            // Make sure the local resized file is cleaned up to free up disk space.
            if (modifiedFile) {
                logs.tempResizedFileDeleting(modifiedFilePath);
                fs.unlinkSync(modifiedFile);
                logs.tempResizedFileDeleted(modifiedFilePath);
            }
        }
        catch (err) {
            logs.errorDeleting(err);
        }
    }
};
const convertFfmpeg = (originalFile, modifiedFile, size) => {
    const s = size.split("x");
    if (s.length != 2) {
        return new Promise((r, e) => e(new Error(`Incorrect size: ${size}`)));
    }
    const w = s[0];
    const h = s[1];
    const promise = spawn(ffmpeg.path, ['-ss', '0', '-i', originalFile, '-f', 'image2', '-vframes', '1', '-vf',
        `scale=if(gte(a\\,1)\\,min(${w}\\,iw)\\,-2):if(gte(a\\,1)\\,-2\\,min(${h}\\,ih))`,
        modifiedFile]);
    const childProcess = promise.childProcess;
    firebase_functions_1.logger.log('[spawn] childProcess.pid: ', childProcess.pid);
    childProcess.stdout.on('data', function (data) {
        firebase_functions_1.logger.log('[spawn] stdout: ', data.toString());
    });
    childProcess.stderr.on('data', function (data) {
        firebase_functions_1.logger.log('[spawn] stderr: ', data.toString());
    });
    return promise;
};
//# sourceMappingURL=resize-image.js.map