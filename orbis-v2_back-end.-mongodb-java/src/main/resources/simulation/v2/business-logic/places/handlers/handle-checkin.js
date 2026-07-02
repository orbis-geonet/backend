import { handleCheckTouchingPlaces } from "./handle-check-touching-places.js";
import { handleDecreaseTouchedPlaces } from "./handle-decrease-touched-places.js";
import { handleElapsedTime } from "./handle-elapsed-time.js";
import { handleGetPlaces } from "./handle-get-places.js";

export function handleCheckin({ placeId, currentTime, manual }) {
  const places = handleGetPlaces();
  const place = places.find(p => p.id === placeId);

  let { newRadius } = handleElapsedTime({ placeId, currentTime });

  if (place.radius >= 490) {
    newRadius = place.radius + 100;
  } else {
    newRadius = 500;
  }

  if (newRadius > 1000) {
    newRadius = 1000;
  }

  place.radius = newRadius;
  place.lastCheckinTime = currentTime;
  place.lastCheckinRadius = newRadius;

  const touchedPlaces = handleCheckTouchingPlaces({ placeId });
  handleDecreaseTouchedPlaces({ placeId, touchedPlaces, currentTime, manual });

  return newRadius;
}
