import { TimeService } from "../../../frontend/time-service.js";
import { Place } from "../models/Place.js";
import { handleElapsedTime } from "./handle-elapsed-time.js";

let lastGetPlacesTime = 0;

export function handleGetPlaces() {
  const currentTime = TimeService.getInstance().currentTime;

  if (currentTime - lastGetPlacesTime > 1000 * 5) {
    Place.places.forEach(place => handleElapsedTime({ placeId: place.id, currentTime }));
  }
  
  return Place.places;
}
