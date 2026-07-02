import { GridModule } from "../../grid/models/Grid.js";
import { Place } from "../models/Place.js";

export function handleDeletePlace({ placeId }) {
  const place = Place.places.find(p => p.id === placeId);
  Place.places = Place.places.filter(p => p.id !== placeId);
  GridModule.getInstance().removePlaceFromGrid(place);
}
