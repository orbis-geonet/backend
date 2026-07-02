import { handleGetPlaces } from "./handle-get-places.js";

export function handleCheckTouchingPlaces({ placeId }) {
  const places = handleGetPlaces();
  const place = places.find(p => p.id === placeId);

  const touchingPlaces = places.filter(p => {
    if (p.id === placeId) {
      return false;
    }

    const distance = Math.sqrt(Math.pow(p.x - place.x, 2) + Math.pow(p.y - place.y, 2)) - p.radius - place.radius;
    return distance < 0;
  });

  return touchingPlaces.map(p => p.id);
}
