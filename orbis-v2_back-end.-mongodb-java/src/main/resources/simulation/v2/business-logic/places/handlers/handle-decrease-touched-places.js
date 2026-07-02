import { LogService } from "../../../frontend/logging-service.js";
import { handleDeletePlace } from "./handle-delete-place.js";
import { handleGetPlaces } from "./handle-get-places.js";

const touchingPlacesModeSelector = document.getElementById("touchingPlacesMode");

export function handleDecreaseTouchedPlaces({ placeId, touchedPlaces, currentTime, manual }) {
  const places = handleGetPlaces();
  const place = places.find(p => p.id === placeId);

  touchedPlaces.forEach((touchedPlaceId) => {
    const touchedPlace = places.find(p => p.id === touchedPlaceId);

    
    let newRadius = touchedPlace.radius;
    if (touchingPlacesModeSelector.value === "weak") {
      newRadius = Math.max(0, touchedPlace.radius - 100);
    } else if (touchingPlacesModeSelector.value === "strong") {
      // Calculate distance between centers
      const distance = Math.sqrt(Math.pow(touchedPlace.x - place.x, 2) + Math.pow(touchedPlace.y - place.y, 2));

      // Calculate overlap (positive if circles overlap, negative if they don't touch)
      const overlap = Math.abs(distance - touchedPlace.radius - place.radius);

      // Calculate new radius considering overlap
      newRadius = Math.max(0, touchedPlace.radius - overlap - 1);
    }

    if (manual) {
      LogService.getInstance().log(`Touched ${touchedPlace.id}: ${touchedPlace.radius.toFixed(2)} -> ${(newRadius).toFixed(2)}`);
    }

    // Update touched place
    touchedPlace.radius = newRadius;
    touchedPlace.lastCheckinTime = currentTime;
    touchedPlace.lastCheckinRadius = newRadius;

    // Delete touched place if radius becomes too small
    if (newRadius <= 0) {
      handleDeletePlace({ placeId: touchedPlace.id });
    }
  });
}
