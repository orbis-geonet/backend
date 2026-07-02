export const images = {};
export const dominantColors = {};
export const dominantBorderColors = {};

const worker = new Worker('./business-logic/images/dominantColorWorker.js');

function getImageData(img) {
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');
    canvas.width = img.width;
    canvas.height = img.height;
    ctx.drawImage(img, 0, 0, img.width, img.height);
    return ctx.getImageData(0, 0, canvas.width, canvas.height).data;
}

function getDominantColor(img, options) {
    return new Promise((resolve) => {
        worker.onmessage = function(event) {
            if (event.data.type === 'dominant') {
                resolve(event.data.color);
            }
        };
        worker.postMessage({ imgData: getImageData(img), options, type: 'dominant' });
    });
}

async function getDominantBorderColor(img, options) {
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');
    canvas.width = img.width;
    canvas.height = img.height;
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    const circleRadius = Math.min(canvas.width, canvas.height) / 2;
    const borderWidth = 10;
    ctx.beginPath();
    ctx.arc(canvas.width / 2, canvas.height / 2, circleRadius - borderWidth, 0, Math.PI * 2);
    ctx.closePath();
    ctx.clip();

    ctx.drawImage(img, 0, 0, img.width, img.height);

    const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
    const borderData = new Uint8ClampedArray(imageData.data.length);
    for (let i = 0; i < imageData.data.length; i += 4) {
        const x = (i / 4) % canvas.width;
        const y = Math.floor((i / 4) / canvas.width);
        const distanceToCenter = Math.sqrt((x - canvas.width / 2) ** 2 + (y - canvas.height / 2) ** 2);
        if (distanceToCenter >= circleRadius - borderWidth && distanceToCenter <= circleRadius) {
            borderData[i] = imageData.data[i];
            borderData[i + 1] = imageData.data[i + 1];
            borderData[i + 2] = imageData.data[i + 2];
            borderData[i + 3] = imageData.data[i + 3];
        }
    }

    return new Promise((resolve) => {
        worker.onmessage = function(event) {
            if (event.data.type === 'border') {
                resolve(event.data.color);
            }
        };
        worker.postMessage({ imgData: borderData, options, type: 'border' });
    });
}

export function addImage(groupId, image) {
    images[groupId] = image;
    getDominantColor(image, { excludeColors: ['255,255,255', '0,0,0'], threshold: 1 }).then((color) => {
        dominantColors[groupId] = color;
        console.log(`Dominant color: ${dominantColors[groupId]}`);
    });
    getDominantBorderColor(image, { excludeColors: [], threshold: 1 }).then((color) => {
        dominantBorderColors[groupId] = color;
        console.log(`Dominant border color: ${dominantBorderColors[groupId]}`);
    });
    console.log(`Added image for group ${groupId}`);
}

export function getImage(groupId) {
    return images[groupId];
}

export function getImageDominantColor(groupId) {
    return dominantColors[groupId];
}

export function getImageBorderDominantColor(groupId) {
    return dominantBorderColors[groupId];
}
