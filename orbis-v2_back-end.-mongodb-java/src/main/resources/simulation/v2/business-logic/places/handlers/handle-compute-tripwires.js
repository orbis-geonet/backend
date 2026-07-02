import { calculateExternalTangents } from '../../../geometry-utils/calculateExternalTangents.js';

import { Tripwire } from '../../tripwire.js';
import { TripwireGroup } from '../../tripwire-group.js';

export async function handleComputeTripwires({ place }) {
  place.closePlaces.forEach(closePlace => {
    if (place.group !== closePlace.group) {
      return;
    }

    const { p1a, p1b, p2a, p2b } = calculateExternalTangents(place.x, place.y, place.radius, closePlace.x, closePlace.y, closePlace.radius);

    new TripwireGroup(place.id, closePlace.id, place.group, [
      new Tripwire(place, closePlace, p1b, p2b, place.group, "first"),
      new Tripwire(place, closePlace, p1a, p2a, place.group, "second"),
      new Tripwire(place, closePlace, { x: place.x, y: place.y }, { x: closePlace.x, y: closePlace.y }, place.group, "center")
    ]);
  });
}
