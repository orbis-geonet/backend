import { handleGetPlaces } from "./handle-get-places.js";

export function handleGetPlaceByPosition({ x, y }) {
  const places = handleGetPlaces();

  return places.find((place) => {
    return Math.sqrt((place.x - x) ** 2 + (place.y - y) ** 2) <= place.radius;
  });
}
