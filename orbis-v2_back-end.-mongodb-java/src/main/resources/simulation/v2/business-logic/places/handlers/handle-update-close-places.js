import { GridModule } from "../../grid/models/Grid.js";

export function handleUpdateClosePlaces({ place }) {
  const places = GridModule.getInstance().getPlacesInGridCells({ x: place.x, y: place.y, numberOfSurroundingCells: 6 });

  place.closePlaces = [];

  places.forEach(p => {
    if (p.group !== place.group) {
      return;
    }

    if (p.id === place.id) {
      return;
    }

    const distance = Math.sqrt(Math.pow(p.x - place.x, 2) + Math.pow(p.y - place.y, 2)) - p.radius - place.radius;

    if (distance < p.radius + place.radius) {
      place.closePlaces.push(p);
    }
  });
}
