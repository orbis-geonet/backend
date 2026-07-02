import { GridModule } from "../../grid/models/Grid.js";
import { Place } from "../models/Place.js";
import { handleCheckTouchingPlaces } from "./handle-check-touching-places.js";
import { handleDecreaseTouchedPlaces } from "./handle-decrease-touched-places.js";

let lastId = 0;

export function handleCreatePlace({ x, y, currentTime, group, manual }) {
  const place = new Place(++lastId, x, y, 500, currentTime, undefined, group);
  GridModule.getInstance().addPlaceToGrid(place);

  const touchedPlaces = handleCheckTouchingPlaces({ placeId: place.id });
  handleDecreaseTouchedPlaces({ placeId: place.id, touchedPlaces, currentTime, manual });

  return place;
}
