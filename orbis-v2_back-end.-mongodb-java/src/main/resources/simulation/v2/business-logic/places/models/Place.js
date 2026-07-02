import { handleComputePolygons } from "../handlers/handle-compute-polygons.js";
import { handleComputeTripwires } from "../handlers/handle-compute-tripwires.js";
import { handleUpdateClosePlaces } from "../handlers/handle-update-close-places.js";

export class Place {
  static places = [];
  static sizeThreshold = 200;
  static distanceThreshold = 1000;

  constructor(id, x, y, radius, lastCheckinTime, lastCheckinRadius, group) {
    this.id = id;

    this.x = x;
    this.y = y;
    this.radius = radius;

    if (!lastCheckinRadius) {
      lastCheckinRadius = radius;
    }

    this.lastCheckinTime = lastCheckinTime;
    this.lastCheckinRadius = lastCheckinRadius;

    this.closePlaces = [];
    this.clusterId = 0;

    if (group !== undefined) {
      this.group = group;
    } else {
      this.group = Math.floor(Math.random() * 4);
    }

    Place.places.push(this);
  }

  async update() {
    handleUpdateClosePlaces({ place: this });
    await handleComputeTripwires({ place: this });
  }
  
  async update2() {
    await handleComputePolygons({ place: this });
  }
}
