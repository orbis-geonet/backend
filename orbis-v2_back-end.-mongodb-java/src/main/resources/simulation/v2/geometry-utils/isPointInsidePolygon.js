/**
 * Implementation of the Ray-Casting algorithm to check if a point is inside a polygon.
 * @param {*} point The point to check { x: number, y: number }
 * @param {*} polygon The polygon to check against { x: number, y: number }[]
 * @returns True if the point is inside the polygon, false otherwise
 */
export function isPointInsidePolygon(point, polygon) {
  let x = point[0], y = point[1];
  let inside = false;

  for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
      let xi = polygon[i][0], yi = polygon[i][1];
      let xj = polygon[j][0], yj = polygon[j][1];

      // Check if point is on the edge of the polygon
      if ((yi === yj) && (yi === y) && (x >= Math.min(xi, xj)) && (x <= Math.max(xi, xj))) {
          return true;
      }

      // Check if line from (xi, yi) to (xj, yj) is crossed
      let intersect = ((yi > y) !== (yj > y)) &&
          (x < (xj - xi) * (y - yi) / (yj - yi) + xi);

      if (intersect) {
          inside = !inside;
      }
  }

  return inside;
}
