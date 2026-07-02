import { GridModule } from "./grid/models/Grid.js";
import { TimeService } from "../frontend/time-service.js";
import { Place } from "./places/models/Place.js";
import { TripwireGroup } from "./tripwire-group.js";
import { Polygons } from "./polygons.js";

import { handleGetPlaceByPosition } from "./places/handlers/handle-get-place-by-position.js";
import { handleGetPlaces } from "./places/handlers/handle-get-places.js";
import { handleCreatePlace } from "./places/handlers/handle-create-place.js";
import { handleCheckin } from "./places/handlers/handle-checkin.js";

export function initBackend() {
  GridModule.getInstance().initGrid();
}

export async function updateBackend() {
  if (TimeService.getInstance().timeIsRunning) {
    TripwireGroup.tripwireGroups = [];

    await Promise.all(Place.places.map(place => place.update()));
    TripwireGroup.tripwireGroups.forEach((tripwireGroup) => tripwireGroup.update());
  
    TripwireGroup.checkCollisions();

    // await handleClusterCircles({ places: Place.places });
    await Promise.all(Place.places.map(place => place.update2()));
    Polygons.getInstance().update();
  }
}

//#region Grid
export function initGrid() {
  GridModule.getInstance().initGrid();
}
//#endregion

//#region Places
export function getPlaceByPosition({ x, y }) {
  return handleGetPlaceByPosition({ x, y });
}

export function getPlaces({ currentTime }) {
  return handleGetPlaces({ currentTime });
}

export function createPlace({ x, y, currentTime, group, manual }) {
  return handleCreatePlace({ x, y, currentTime: currentTime.getTime(), group, manual });
}

export function checkin({ placeId, currentTime, manual }) {
  return handleCheckin({ placeId, currentTime: currentTime.getTime(), manual });
}
//#endregion

//#region Tripwires
export function getTripwireGroups() {
  return TripwireGroup.tripwireGroups;
}
//#endregion

//#region Polygons
export function getPolygonByPosition({ x, y }) {
  return Polygons.getInstance().getPolygonByPosition({ x, y });
}

export function getPolygons() {
  return Polygons.getInstance().polygons;
}
//#endregion

//#region Images
export * from './images/images.js';
//#endregion
