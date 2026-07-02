import { addImage, checkin, createPlace, getImage, getPlaceByPosition, getPlaces, getPolygons, getTripwireGroups, initGrid } from '../business-logic/index.js';
import { Place } from '../business-logic/places/models/Place.js';
import { LogService } from './logging-service.js';
import { TimeService } from './time-service.js';
import { getImageDominantColor, getImageBorderDominantColor } from '../business-logic/images/images.js';
import { Tripwire } from '../business-logic/tripwire.js';
import { Polygons } from '../business-logic/polygons.js';
import { GridModule } from '../business-logic/grid/models/Grid.js';

const colors = ['red', 'blue', 'green', 'yellow', 'purple', 'orange', 'pink', 'brown', 'black', 'white'];  
const colorsWithTransparency = ['rgba(255, 0, 0, 0.5)', 'rgba(0, 0, 255, 0.5)', 'rgba(0, 255, 0, 0.5)', 'rgba(255, 255, 0, 0.5)', 'rgba(128, 0, 128, 0.5)', 'rgba(255, 165, 0, 0.5)', 'rgba(255, 192, 203, 0.5)', 'rgba(165, 42, 42, 0.5)', 'rgba(0, 0, 0, 0.5)', 'rgba(255, 255, 255, 0.5)'];

export class CanvasService {
  canvas = document.getElementById('canvas');
  ctx = this.canvas.getContext('2d', {
    willReadFrequently: true,
  });

  scalingFactor = 20;
  showPlacesForPolygons = [];

  static instance;

  static getInstance() {
    if (!CanvasService.instance) {
      CanvasService.instance = new CanvasService();
    }

    return CanvasService.instance;
  }

  constructor() {
    if (!CanvasService.instance) {
      CanvasService.instance = this;

      CanvasService.instance.resizeCanvas();
      CanvasService.instance.initCanvasClickHandler();
      CanvasService.instance.initCanvasContextMenuHandler();
      CanvasService.instance.initCanvasResizeHandler();
      CanvasService.instance.initUploadImage();
      CanvasService.instance.initClearCanvasButton();
    }
    
    return CanvasService.instance;
  }

  resizeCanvas() {
    this.canvas.width = 500;
    this.canvas.width = this.canvas.clientWidth;
    this.canvas.height = 500;
    this.canvas.height = this.canvas.clientHeight;

    this.canvasWidth = this.canvas.width;
    this.canvasHeight = this.canvas.height;

    initGrid();
  }

  clearCanvas() {
    this.ctx.clearRect(0, 0, canvas.width, canvas.height);
    this.ctx.fillStyle = '#E0E0E0';
    this.ctx.fillRect(0, 0, canvas.width, canvas.height);
    this.ctx.fillStyle = 'black';
  }

  drawPlaces() {
    const currentTime = TimeService.getInstance().currentTime;
    const places = getPlaces(currentTime)
    const polygons = getPolygons();

    let placesInPolygons = [];
    if (document.getElementById('showPlacesInsidePolygons').checked) {
      // Check which places are in polygons and associate them with that polygons id
      polygons.forEach((polygon) => {
        polygon.places.forEach((place) => {
          placesInPolygons.push({ placeId: place.id, polygonId: polygon.id });
        });
      });
    }

    places.forEach((place) => {
      // If the place is in a polygon, hide it unless its polygon is in the showPlacesForPolygons array
      if (document.getElementById('showPlacesInsidePolygons').checked) {
        let partOfPolygon = null;
        placesInPolygons.forEach(p => {
          if (p.placeId === place.id) {
            partOfPolygon = p.polygonId;
          }
        });
  
        if (partOfPolygon !== null && !this.showPlacesForPolygons.includes(partOfPolygon)) {
          return;
        }
      }

      const { x, y } = this.worldToCanvasCoordinates(place.x, place.y);
      const radius = this.worldToCanvasSize(place.radius);

      this.ctx.beginPath();
      this.ctx.arc(x, y, radius, 0, 2 * Math.PI);

      if (document.getElementById('showPolygonOutline').checked) {
        if (getImageDominantColor(places.group)) {
          const dominantColor = getImageDominantColor(place.group);
          this.ctx.strokeStyle = `rgba(${dominantColor[0]}, ${dominantColor[1]}, ${dominantColor[2]}, 0.5)`;
        } else {
          this.ctx.strokeStyle = colors[place.group];
        }

        this.ctx.lineWidth = 3;
        this.ctx.stroke();
        this.ctx.lineWidth = 1;
        this.ctx.strokeStyle = 'black';
      }

      const showGroupColorsCheckbox = document.getElementById('showGroupColors');
      if (showGroupColorsCheckbox.checked) {
        this.fillPlace(x, y, radius, place.group);
      }

      if (document.getElementById('showCenters').checked) {
        const image = getImage(place.group);
        if (image) {
          this.drawImage(image, x, y, radius, place.group);
        }
      }
    });
  }

  fillPlace(x, y, radius, group) {
    const fillOption = document.querySelector('input[name="polygonFill"]:checked').id;
    const image = getImage(group);

    if (fillOption === 'noFillPolygon') {
      return;
    }

    if (fillOption === 'fillPolygonWithColor') {
      this.ctx.fillStyle = colorsWithTransparency[group];
      this.ctx.fill();
      return;
    }

    if (image && fillOption === 'fillPolygonWithIcon') {
      this.ctx.save();
      this.ctx.clip();
      this.ctx.drawImage(image, x - radius, y - radius, radius * 2, radius * 2);
      this.ctx.restore();
      return;
    }

    if (image && fillOption === 'fillPolygonWithIconMaintainingAspectRatio') {
      this.ctx.save();
      this.ctx.clip();
      this.ctx.drawImage(image, x - radius, y - radius, radius * 2, radius * 2);
      this.ctx.restore();
      return;
    }

    if (image && fillOption === 'fillPolygonWithIconBorder') {
      return;
    }

    if (image && fillOption === 'fillPolygonWithIconBorderDominantColor') {
      const dominantColor = getImageBorderDominantColor(group);
      this.ctx.fillStyle = `rgba(${dominantColor[0]}, ${dominantColor[1]}, ${dominantColor[2]}, 0.5)`;
      this.ctx.fill();
      this.ctx.fillStyle = 'black';
    }

    if (image && fillOption === 'fillPolygonWithIconBorderContinuously') {
      return;
    }

    if (image && fillOption === 'fillPolygonWithDominantColor') {
      const dominantColor = getImageDominantColor(group);
      this.ctx.fillStyle = `rgba(${dominantColor[0]}, ${dominantColor[1]}, ${dominantColor[2]}, 0.5)`;
      this.ctx.fill();
      this.ctx.fillStyle = 'black';
    }
  }

  drawTripwires() {
    const tripwireGroups = getTripwireGroups();

    tripwireGroups.forEach((tripwireGroup) => {
      tripwireGroup.tripwires.forEach((tripwire) => {
        const p1 = this.worldToCanvasCoordinates(tripwire.p1.x, tripwire.p1.y);
        const p2 = this.worldToCanvasCoordinates(tripwire.p2.x, tripwire.p2.y);

        this.ctx.beginPath();

        this.ctx.moveTo(p1.x, p1.y);
        this.ctx.lineTo(p2.x, p2.y);

        if (tripwire.isColliding) {
          this.ctx.strokeStyle = 'rgba(255, 0, 0, 0.5)';
        } else {
          this.ctx.strokeStyle = 'rgba(0, 0, 0, 0.3)';
        }
        
        this.ctx.lineWidth = 2;
        this.ctx.stroke();
        this.ctx.lineWidth = 1;
        this.ctx.strokeStyle = 'black';
      });
    });
  }

  drawPolygons() {
    const polygons = getPolygons();

    for (let p of polygons) {
      const { places, mergedPolygon } = p;
  
      if (!mergedPolygon) {
        continue;
      }

      this.drawPolygonCenter(p.center[0], p.center[1], p.center.distance, places[0].group);

      // Draw main polygon
      let polygon = mergedPolygon[0].map(point => [
        CanvasService.getInstance().worldToCanvasSize(point[0]),
        CanvasService.getInstance().worldToCanvasSize(point[1]),
      ]);
  
      this.ctx.beginPath();
      this.ctx.moveTo(polygon[0][0], polygon[0][1]);
  
      for (let i = 1; i < polygon.length; i++) {
        this.ctx.lineTo(polygon[i][0], polygon[i][1]);
      }
  
      // Draw holes
      for (let hole of p.holes) {
        hole = hole.map(point => [
          CanvasService.getInstance().worldToCanvasSize(point[0]),
          CanvasService.getInstance().worldToCanvasSize(point[1]),
        ]);
  
        this.ctx.moveTo(hole[0][0], hole[0][1]);
  
        for (let i = 1; i < hole.length; i++) {
          this.ctx.lineTo(hole[i][0], hole[i][1]);
        }
      }

      this.ctx.closePath();

      if (document.getElementById('showPolygonOutline').checked) {
        if (getImageBorderDominantColor(places[0].group)) {
          const dominantColor = getImageBorderDominantColor(places[0].group);
          this.ctx.strokeStyle = `rgba(${dominantColor[0]}, ${dominantColor[1]}, ${dominantColor[2]}, 0.5)`;
        } else {
          this.ctx.strokeStyle = colors[places[0].group];
        }

        this.ctx.lineWidth = 3;
        this.ctx.stroke();
        this.ctx.strokeStyle = 'black';
        this.ctx.lineWidth = 1;
      }
      this.fillPolygon(polygon, p.center, places[0].group);
      this.drawPolygonCenter(p.center[0], p.center[1], p.center.distance, places[0].group);
    }
  }

  fillPolygon(polygon, center, group) {
    const fillOption = document.querySelector('input[name="polygonFill"]:checked').id;
    const image = getImage(group);

    if (fillOption === 'noFillPolygon') {
      return;
    }

    if (fillOption === 'fillPolygonWithColor') {
      this.ctx.fillStyle = colorsWithTransparency[group];
      this.ctx.fill('evenodd');
      return;
    }

    if (image && fillOption === 'fillPolygonWithIcon') {
      this.fillPolygonWithImage(image, polygon);
      return;
    }

    if (image && fillOption === 'fillPolygonWithIconMaintainingAspectRatio') {
      this.fillPolygonWithImagePreserveAspect(image, polygon);
      return;
    }

    if (image && fillOption === 'fillPolygonWithIconBorder') {
      this.fillPolygonWithIconBorder(polygon, center[0], center[1], center.distance, image, group);
      return;
    }

    if (image && fillOption === 'fillPolygonWithIconBorderDominantColor') {
      this.fillPolygonWithDominantBorderColor(group);
      return;
    }

    if (image && fillOption === 'fillPolygonWithIconBorderContinuously') {
      this.fillPolygonWithIconBorderContinuously(polygon, center[0], center[1], center.distance, image, group);
      return;
    }

    if (image && fillOption === 'fillPolygonWithDominantColor') {
      this.fillPolygonWithDominantColor(group);
      return;
    }
  }

  drawPolygonCenter(x, y, distance, group) {
    if (!document.getElementById('showCenters').checked) {
      return;
    }

    const center = [
      CanvasService.getInstance().worldToCanvasSize(x),
      CanvasService.getInstance().worldToCanvasSize(y),
    ];

    let drawn = false;

    const image = getImage(group);

    if (!drawn && image && !document.getElementById('fillPolygonWithIcon').checked) {
      // Draw the image as a circle as large as possible
      const radius = this.worldToCanvasSize(distance);
      this.drawImage(image, center[0], center[1], radius, group);
      drawn = true;
    }

    if (!drawn) {
      // Black circle if no image is available for the group
      this.ctx.beginPath();
      this.ctx.arc(center[0], center[1], 5, 0, 2 * Math.PI);
      this.ctx.fillStyle = "black";
      this.ctx.fill();
    }
  }

  drawImage(img, x, y, radius, group) {
    // Create a circular clipping path
    this.ctx.save();
    this.ctx.beginPath();
    this.ctx.arc(x, y, radius, 0, Math.PI * 2);
    this.ctx.closePath();
    this.ctx.clip();

    // Draw the image as a pattern within the circular clipping path
    this.ctx.drawImage(img, x - radius, y - radius, radius * 2, radius * 2);

    // Draw a border around the circle
    if (document.getElementById('showIconOutline').checked) {
      if (getImageDominantColor(group)) {
        const dominantColor = getImageDominantColor(group);
        this.ctx.strokeStyle = `rgba(${dominantColor[0]}, ${dominantColor[1]}, ${dominantColor[2]}, 0.5)`;
      } else {
        this.ctx.strokeStyle = colors[group];
      }

      this.ctx.lineWidth = 5;
      this.ctx.stroke();
      this.ctx.lineWidth = 1;
    }

    // Restore the previous context state to remove the clipping path
    this.ctx.restore();
  }

  fillPolygonWithImage(img, points) {
    // Calculate the bounding box of the polygon
    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
    for (let i = 0; i < points.length; i++) {
      minX = Math.min(minX, points[i][0]);
      minY = Math.min(minY, points[i][1]);
      maxX = Math.max(maxX, points[i][0]);
      maxY = Math.max(maxY, points[i][1]);
    }
    const width = maxX - minX;
    const height = maxY - minY;

    this.ctx.save();
    this.ctx.clip();

    // Draw the image within the clipping path, using the polygon's dimensions
    this.ctx.drawImage(img, minX, minY, width, height);

    // Reset clipping to avoid affecting future drawings
    this.ctx.restore();
  }

  fillPolygonWithImagePreserveAspect(img, points) {
    // Calculate the bounding box of the polygon
    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
    for (let i = 0; i < points.length; i++) {
      minX = Math.min(minX, points[i][0]);
      minY = Math.min(minY, points[i][1]);
      maxX = Math.max(maxX, points[i][0]);
      maxY = Math.max(maxY, points[i][1]);
    }
    const width = maxX - minX;
    const height = maxY - minY;

    // Calculate scaling factors to maintain aspect ratio and cover the entire bounding box
    const imgWidth = img.width;
    const imgHeight = img.height;
    const aspectRatio = imgWidth / imgHeight;
    let scaledWidth = width;
    let scaledHeight = height;
    if (width / height > aspectRatio) {
        scaledHeight = width / aspectRatio;
    } else {
        scaledWidth = height * aspectRatio;
    }

    const offsetX = minX + (width - scaledWidth) / 2;
    const offsetY = minY + (height - scaledHeight) / 2;

    this.ctx.save();
    this.ctx.beginPath();
    this.ctx.moveTo(points[0][0], points[0][1]);
    for (let i = 1; i < points.length; i++) {
        this.ctx.lineTo(points[i][0], points[i][1]);
    }
    this.ctx.closePath();
    this.ctx.clip();

    // Draw the image within the clipping path, maintaining aspect ratio and covering the entire bounding box
    this.ctx.drawImage(img, offsetX, offsetY, scaledWidth, scaledHeight);

    // Reset clipping to avoid affecting future drawings
    this.ctx.restore();
  }

  fillPolygonWithIconBorder(p, x, y, distance, image, group) {
    const center = [
      CanvasService.getInstance().worldToCanvasSize(x),
      CanvasService.getInstance().worldToCanvasSize(y),
    ];

    const radius = this.worldToCanvasSize(distance);
    // this.drawImage(image, center[0], center[1], radius, group);

    const dataPoint = this.ctx.getImageData(center[0], center[1] - radius + 2, 1, 1).data;
    const color = `rgba(${dataPoint[0]}, ${dataPoint[1]}, ${dataPoint[2]}, ${dataPoint[3]})`;

    this.ctx.fillStyle = color;
    this.ctx.fill();
    this.ctx.fillStyle = 'black';
  }

  fillPolygonWithDominantBorderColor(group) {
    const dominantColor = getImageBorderDominantColor(group);
    if (!dominantColor) {
      return;
    }

    this.ctx.fillStyle = `rgba(${dominantColor[0]}, ${dominantColor[1]}, ${dominantColor[2]}, 0.5)`;
    this.ctx.fill();
    this.ctx.fillStyle = 'black';
  }

  fillPolygonWithIconBorderContinuously(p, x, y, distance, image, group) {
    const radius = this.worldToCanvasSize(distance);

    const center = [
      CanvasService.getInstance().worldToCanvasSize(x),
      CanvasService.getInstance().worldToCanvasSize(y),
    ];

    // Find max distance from center to polygon edge
    let maxDistance = 0;
    for (let i = 0; i < p.length; i++) {
      const d = Math.sqrt(Math.pow(center[0] - p[i][0], 2) + Math.pow(center[1] - p[i][1], 2));
      maxDistance = Math.max(maxDistance, d);
    }

    const circumference = 2 * Math.PI * maxDistance;
    const spacing = 1;
    const numSegments = Math.round(circumference / spacing);

    this.ctx.save();
    this.ctx.clip();
    this.ctx.lineWidth = 2;
    for (let angle = 0; angle < 2 * Math.PI; angle += 2 * Math.PI / numSegments)  {
      const dataPointX = center[0] + (radius - 1) * Math.cos(angle) ;
      const dataPointY = center[1] + (radius - 1) * Math.sin(angle) ;

      const dataPoint = this.ctx.getImageData(dataPointX, dataPointY, 1, 1).data;
      const color = `rgba(${dataPoint[0]}, ${dataPoint[1]}, ${dataPoint[2]}, ${dataPoint[3]})`;

      const endX = center[0] + maxDistance * Math.cos(angle);
      const endY = center[1] + maxDistance * Math.sin(angle);

      this.ctx.beginPath();
      this.ctx.moveTo(dataPointX, dataPointY);
      this.ctx.lineTo(endX, endY);
      this.ctx.strokeStyle = color;
      this.ctx.stroke();
    }
    this.ctx.restore();
  }

  fillPolygonWithDominantColor(group) {
    const dominantColor = getImageDominantColor(group);
    this.ctx.fillStyle = `rgba(${dominantColor[0]}, ${dominantColor[1]}, ${dominantColor[2]}, 0.5)`;
    this.ctx.fill();
    this.ctx.fillStyle = 'black';
  }

  //#region Translations between world and canvas coordinates
  worldToCanvasCoordinates(x, y) {
    return {
      x: x / this.scalingFactor,
      y: y / this.scalingFactor,
    };
  }

  worldToCanvasSize(value) {
    return value / this.scalingFactor;
  }

  canvasToWorldCoordinates(x, y) {
    return {
      x: x * this.scalingFactor,
      y: y * this.scalingFactor,
    };
  }

  canvasToWorldSize(value) {
    return  value * this.scalingFactor;
  }
  //#endregion

  initCanvasClickHandler() {
    this.canvas.addEventListener("click", async (event) => {
      const worldCoords = this.canvasToWorldCoordinates(event.clientX - this.canvas.getBoundingClientRect().left, event.clientY - this.canvas.getBoundingClientRect().top);

      const place = getPlaceByPosition({ x: worldCoords.x, y: worldCoords.y });
      const currentTime = TimeService.getInstance().currentTime;

      if (place) {
        const previousRadius = place.radius;
        const newRadius = checkin({ placeId: place.id, currentTime, manual: true });
        LogService.getInstance().log(`Place ${place.id}: ${previousRadius.toFixed(2)} -> ${(newRadius).toFixed(2)}`);
      } else {
        const group = parseInt(document.querySelector('input[name="group"]:checked').value);
        const place = createPlace({ x: worldCoords.x, y: worldCoords.y, currentTime, group, manual: true });
        LogService.getInstance().log(`Place ${place.id}: 0 -> ${place.radius.toFixed(2)}`);
      }
    });
  }

  initCanvasContextMenuHandler() {
    this.canvas.addEventListener("contextmenu", async (event) => {
      event.preventDefault();

      const worldCoords = this.canvasToWorldCoordinates(event.clientX - this.canvas.getBoundingClientRect().left, event.clientY - this.canvas.getBoundingClientRect().top);

      const place = getPlaceByPosition({ x: worldCoords.x, y: worldCoords.y });

      if (place) {
        LogService.getInstance().log('');
        LogService.getInstance().log(`Place ${place.id}:`);
        LogService.getInstance().log(`Group: ${place.group}`);
        LogService.getInstance().log(`X: ${place.x} , Y: ${place.y}`);
        LogService.getInstance().log(`Radius: ${place.radius.toFixed(2)}`);
        LogService.getInstance().log(`Close Places Ids: ${place.closePlaces.map(p => p.id).join(', ')}`);

        if (this.previousPlace) {
          const distance = Math.sqrt(Math.pow(place.x - this.previousPlace.x, 2) + Math.pow(place.y - this.previousPlace.y, 2)) - place.radius - this.previousPlace.radius;
          LogService.getInstance().log(`Distance to Previous: ${distance.toFixed(2)}`);
        }

        LogService.getInstance().log(' ');
        this.previousPlace = place;
      }
    });
  }

  initCanvasResizeHandler() {
    window.addEventListener('resize', () => {
      this.resizeCanvas();
    });
  }

  initUploadImage() {
    function uploadImage(event) {
      const fileInput = event.target;
      if (fileInput.files.length > 0) {
        const file = fileInput.files[0];
        const reader = new FileReader();
        reader.onload = function (event) {
          const img = new Image();
          img.onload = function () {
            const group = parseInt(document.querySelector('input[name="group"]:checked').value);
            addImage(group, img);
            console.log(`Image uploaded for group ${group}`);
            fileInput.value = null;
          };
          img.src = event.target.result;
        };
        reader.readAsDataURL(file);
      } else {
        console.error("Please select a file and enter a radius.");
      }
    }

    document.getElementById("imageUploadButton").addEventListener("click", function () {  document.getElementById("imageUpload").click();});
    document.getElementById("imageUpload").addEventListener("change", uploadImage);
  }

  initClearCanvasButton() {
    document.getElementById("clearCanvas").addEventListener("click", function () {
      TimeService.getInstance().currentTime = new Date();
      Place.places = [];
      Tripwire.tripwires = [];
      Tripwire.tripwireGroups = [];
      Polygons.polygons = [];
      GridModule.getInstance().initGrid();
    });
  }
}
