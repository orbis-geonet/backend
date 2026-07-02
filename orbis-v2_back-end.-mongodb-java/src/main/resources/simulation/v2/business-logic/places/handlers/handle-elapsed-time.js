import { Place } from "../models/Place.js";
import { handleDeletePlace } from "./handle-delete-place.js";

export function handleElapsedTime({ placeId, currentTime }) {
  const place = Place.places.find(p => p.id === placeId);
  const { lastCheckinTime, lastCheckinRadius } = place;

  const elapsedTime = Math.abs(lastCheckinTime - currentTime);

  let newRadius = lastCheckinRadius;

  if (lastCheckinRadius > 500) {
    const radiusChange = (lastCheckinRadius - 500) * (elapsedTime / (24 * 60 * 60 * 1000));
    newRadius = Math.max(500, lastCheckinRadius - radiusChange);
  }

  const radiusChange = newRadius * (elapsedTime / (24 * 60 * 60 * 1000 * 365));

  if (newRadius - radiusChange < 0) {
    handleDeletePlace({ placeId });
  } else {
    newRadius = Math.max(0, newRadius - radiusChange);
  }

  place.radius = newRadius;

  return { newRadius };
}
