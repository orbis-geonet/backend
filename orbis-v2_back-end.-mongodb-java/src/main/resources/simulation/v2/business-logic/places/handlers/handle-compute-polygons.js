import { createExternalPolygon } from '../../../geometry-utils/createExternalPolygon.js';
import { createMixedPolygon1 } from '../../../geometry-utils/createMixedPolygon1.js';
import { createMixedPolygon2 } from '../../../geometry-utils/createMixedPolygon2.js';

import { TripwireGroup } from '../../tripwire-group.js';

export async function handleComputePolygons({ place }) {
  const tripwireStates = TripwireGroup.getState(place.id);

  // Create external polygons
  place.polygons = [];

  for (let closePlace of place.closePlaces) {
    const tripwireGroup = tripwireStates.find(tripwireGroup => tripwireGroup.toPlace === closePlace.id);

    if (tripwireGroup === undefined) {
      continue;
    }

    if (tripwireGroup.state === "connected") {
      place.polygons.push(createExternalPolygon(place, closePlace));
    } else if (tripwireGroup.state === "internal1") {
      place.polygons.push(createMixedPolygon1(place, closePlace));
    } else if (tripwireGroup.state === "internal2") {
      place.polygons.push(createMixedPolygon2(place, closePlace));
    }
  }

  // Merge polygons
  if (place.polygons.length === 0) {
    place.polygon = null;
  } else if (place.polygons.length === 1) {
    place.polygon = place.polygons.map(polygon => polygon.map(point => [point.x, point.y]));
  } else if (place.polygons.length > 1) {
    const formattedPolygons = place.polygons.map(polygon => [polygon.map(point => [point.x, point.y])]);
    formattedPolygons.push(formattedPolygons[0]);
    place.polygon = polygonClipping.union(...formattedPolygons)[0];
  }
}
