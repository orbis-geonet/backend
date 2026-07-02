import { calculateExternalTangents } from "./calculateExternalTangents.js";
import { calculateArcPoints } from "./calculateArcPoints.js";

/**
 * Creates a polygon representing the area between two places
 * @param {*} place1 A circle with properties x, y, and radius
 * @param {*} place2 A circle with properties x, y, and radius
 * @returns An array of points representing the polygon
 */
export function createExternalPolygon(place1, place2) {
  // Calculate tangent points
  var { p1a, p1b, p2a, p2b } = calculateExternalTangents(place1.x, place1.y, place1.radius, place2.x, place2.y, place2.radius);

  // Discretize the arc from p2b to p2a and add the points to the polygon
  var startAngle2 = Math.atan2(p2b.y - place2.y, p2b.x - place2.x);
  var endAngle2 = Math.atan2(p2a.y - place2.y, p2a.x - place2.x);
  const arcPoints1 = calculateArcPoints(place2, place2.radius, startAngle2, endAngle2, 20);

  // Discretize the arc from p1a to p1b and add the points to the polygon
  var startAngle1 = Math.atan2(p1a.y - place1.y, p1a.x - place1.x);
  var endAngle1 = Math.atan2(p1b.y - place1.y, p1b.x - place1.x);
  const arcPoints2 = calculateArcPoints(place1, place1.radius, startAngle1, endAngle1, 20);

  // Combine points to form the polygon
  const polygon = [
    ...arcPoints1.map(point => ({ x: parseFloat(point.x.toFixed(1)), y: parseFloat(point.y.toFixed(1)) })),
    ...arcPoints2.map(point => ({ x: parseFloat(point.x.toFixed(1)), y: parseFloat(point.y.toFixed(1)) })),
  ];

  // Ensure the polygon is closed
  polygon.push(polygon[0]);

  return polygon;
}
