import { findCenterOfPolygon } from "../geometry-utils/findPolygonCenter.js";
import { isPointInsidePolygon } from "../geometry-utils/isPointInsidePolygon.js";

import { handleGetPlaces } from "./places/handlers/handle-get-places.js";
import { TripwireGroup } from "./tripwire-group.js";

export class Polygons {
  static id = 0;
  static const = ['rgba(255, 0, 0, 0.5)', 'rgba(0, 0, 255, 0.5)', 'rgba(0, 255, 0, 0.5)', 'rgba(255, 255, 0, 0.5)', 'rgba(128, 0, 128, 0.5)', 'rgba(255, 165, 0, 0.5)', 'rgba(255, 192, 203, 0.5)', 'rgba(165, 42, 42, 0.5)', 'rgba(0, 0, 0, 0.5)', 'rgba(255, 255, 255, 0.5)'];

  precision = 20.0;
  polygons = [];

  constructor() {
    if (!Polygons.instance) {
      Polygons.instance = this;
    }

    return Polygons.instance;
  }

  static getInstance() {
    if (!this.instance) {
      this.instance = new Polygons();
    }

    return this.instance;
  }

  update() {
    Polygons.id = 0;

    const places = handleGetPlaces();

    this.polygons = [];

    const visited = new Set();
    const consideredGroups = new Set();

    for (let place of places) {
        if (!visited.has(place)) {
            const currentGroup = new Set();
            const queue = [place];
    
            while (queue.length > 0) {
                const currentPlace = queue.shift();
                visited.add(currentPlace);
                currentGroup.add(currentPlace.id);

                for (let otherPlace of currentPlace.closePlaces) {
                    if (!visited.has(otherPlace)) {
                      const state = TripwireGroup.getStateBetween(currentPlace.id, otherPlace.id);

                      if (state !== 'broken' && state !== 'unknown') {
                        queue.push(otherPlace);
                      }
                    }
                }
            }

            // Check if the current group has been considered before
            if (!consideredGroups.has(Array.from(currentGroup).toString())) {
                consideredGroups.add(Array.from(currentGroup).toString());

                // Generate a single polygon for the connected places
                const connectedPlaces = Array.from(currentGroup).map(id => places.find(place => place.id === id));

                const polygons = connectedPlaces.map(place => place.polygons).flat();
                const mappedPolygons = polygons.map(polygon => [polygon.map(point => [point.x, point.y])]);

                if (mappedPolygons.length === 0) {
                    // this.polygons.push({
                    //     places: connectedPlaces,
                    //     polygons: [],
                    //     mergedPolygon: [],
                    //     holes: [],
                    // });
                    continue;
                }

                let mergedPolygon = polygonClipping.union(...mappedPolygons);
                const originalMergedPolygon = mergedPolygon;
                let holes = [];
                
                let center;
                if (mergedPolygon[0].length > 1) {
                    const checkHoles = Polygons.checkHoles(mergedPolygon[0]);
                    mergedPolygon = [[checkHoles.polygon, ...checkHoles.holesWithoutPlaces]];
                    holes = checkHoles.holesWithPlaces.map(hole => turf.polygonSmooth(turf.polygon([hole]), { iterations: 2 }).features[0].geometry.coordinates[0]);

                    if (holes.length > 0) {
                      center = findCenterOfPolygon(originalMergedPolygon[0], this.precision, []);
                    } else {
                      center = findCenterOfPolygon([checkHoles.polygon], this.precision);
                    }
                } else {
                  center = findCenterOfPolygon(mergedPolygon[0], this.precision, []);
                }

                const turfPolygon = turf.polygon(mergedPolygon[0]);
                const smoothedPolygons = turf.polygonSmooth(turfPolygon, { iterations: 2 });

                this.polygons.push({
                    id: Polygons.id++,
                    places: connectedPlaces,
                    polygons: mappedPolygons,
                    mergedPolygon: [smoothedPolygons.features[0].geometry.coordinates[0]],
                    holes,
                    center,
                });
            }
        }
    }
  }

  static checkHoles(polygon) {
    const originalPolygon = polygon[0];
    const holes = polygon.slice(1);
    let holesWithoutPlaces = [];
    let holesWithPlaces = [];

    const places = handleGetPlaces();
  
    for (let hole of holes) {
      let hasPlaceInside = false;
      for (let place of places) {
        if (isPointInsidePolygon([place.x, place.y], hole)) {
          hasPlaceInside = true;
          holesWithPlaces.push(hole);
          break;
        }
      }

      if (!hasPlaceInside) {
        holesWithoutPlaces.push(hole);
      }
    }

    return {
      polygon: originalPolygon,
      holesWithoutPlaces,
      holesWithPlaces,
    }
  }

  getPolygonByPosition({ x, y }) {
    return this.polygons.find(polygon => {
      if (polygon.mergedPolygon && polygon.mergedPolygon.length === 0) {
        return false;
      }

      return isPointInsidePolygon([x, y], polygon.mergedPolygon[0]);
    });
  }
}
