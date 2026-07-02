import { doLineSegmentsIntersect } from '../geometry-utils/doLineSegmentsIntersect.js';
import { findIntersection } from '../geometry-utils/findIntersection.js';

export class TripwireGroup {
  static id = 0;
  static tripwireGroups = [];

  state = "connected";
  connected = true;

  constructor(placeId, otherPlaceId, group, tripwires) {
    this.id = TripwireGroup.id++;
    TripwireGroup.tripwireGroups.push(this);

    this.placeId = placeId;
    this.otherPlaceId = otherPlaceId;
    this.group = group;
    this.tripwires = tripwires;
  }

  static getState(placeId) {
    let states = [];

    TripwireGroup.tripwireGroups.forEach(tripwireGroup => {
      if (tripwireGroup.placeId === placeId) {
        const tripwireGroupId = tripwireGroup.id;
        const toPlace = tripwireGroup.tripwires[0].endPlace.id;
        let state = "connected";

        if (!tripwireGroup.connected) {
          state = "broken";
          states.push({ tripwireGroupId, toPlace, state });
          return;
        }

        tripwireGroup.tripwires.forEach(tripwire => {
          const type = tripwire.location;
          const collision = tripwire.isColliding;

          if (tripwire.location === "center" && tripwire.isColliding) {
            state = "broken";
          }

          if (tripwire.location === "first" && tripwire.isColliding) {
            state = "internal1";
          }

          if (tripwire.location === "second" && tripwire.isColliding) {
            state = "internal2";
          }
        });

        TripwireGroup.tripwireGroups.find(tg => tg.id === tripwireGroupId).state = state;
        states.push({ tripwireGroupId, toPlace, state });
      }
    });

    return states;
  }

  static getStateBetween(placeId, otherPlaceId) {
    const tripwireGroup = TripwireGroup.tripwireGroups.find(tg => tg.placeId === placeId && tg.otherPlaceId === otherPlaceId || tg.placeId === otherPlaceId && tg.otherPlaceId === placeId);

    if (!tripwireGroup) {
      return "unknown";
    }

    let state = "connected";

    if (!tripwireGroup.connected) {
      state = "broken";
      return state;
    }

    tripwireGroup.tripwires.forEach(tripwire => {
      if (tripwire.location === "center" && tripwire.isColliding) {
        state = "broken";
      }

      if (tripwire.location === "first" && tripwire.isColliding) {
        state = "internal1";
      }

      if (tripwire.location === "second" && tripwire.isColliding) {
        state = "internal2";
      }
    });

    return state;
  }

  update() {
    this.tripwires.forEach(tripwire => tripwire.update());

    let brokenTripwires = 0;

    this.tripwires.forEach(tripwire => {
      if (tripwire.isColliding) {
        brokenTripwires++;
      }
    })

    this.connected = true;

    if (brokenTripwires >= 2) {
      this.connected = false;
    }
  }

  static checkCollisions() {
    // Go through each tripwire group and check for collisions with other tripwire groups
    for (let tripwireGroup of TripwireGroup.tripwireGroups) {
      for (let otherTripwireGroup of TripwireGroup.tripwireGroups) {
        if (tripwireGroup === otherTripwireGroup) {
          continue;
        }

        if (tripwireGroup.group === otherTripwireGroup.group) {
          continue;
        }

        for (let tripwire of tripwireGroup.tripwires) {
          for (let otherTripwire of otherTripwireGroup.tripwires) {
            const collision = doLineSegmentsIntersect(tripwire.p1, tripwire.p2, otherTripwire.p1, otherTripwire.p2);
            if (collision) {
              if (tripwire.group > otherTripwire.group) {
                tripwire.isColliding = true;
              } else {
                otherTripwire.isColliding = true;
              }
            }
            }
        }
      }
    }
  }

  draw(ctx) {
    // if (this.connected) {
    //   this.tripwires.forEach(tripwire => tripwire.draw(ctx));
    // }
  }
}