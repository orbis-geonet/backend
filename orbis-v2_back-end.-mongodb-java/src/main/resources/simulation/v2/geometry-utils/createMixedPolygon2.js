import { calculateExternalTangents } from './calculateExternalTangents.js';
import { calculateInternalTangents } from './calculateInternalTangents.js'
import { findIntersection } from './findIntersection.js';
import { calculateArcPoints } from './calculateArcPoints.js';

/**
 * Creates a polygon representing the area between two places
 * @param {*} place1 A circle with properties x, y, and radius
 * @param {*} place2 A circle with properties x, y, and radius
 * @returns An array of points representing the polygon
 */
export function createMixedPolygon2(place1, place2) {
  // Calculate tangent points and their intersection point
  const { p1a, p1b, p2a, p2b } = calculateExternalTangents(place1.x, place1.y, place1.radius, place2.x, place2.y, place2.radius);
  const { p1a: p1a2, p1b: p1b2, p2a: p2a2, p2b: p2b2 } = calculateInternalTangents(place2.x, place2.y, place2.radius, place1.x, place1.y, place1.radius);
  const intersectionPoint = findIntersection(p1b2, p2b2, p1a2, p2a2);

  // Discretize the arcs
  const arcPoints1 = calculateArcPoints({ x: place1.x, y: place1.y }, place1.radius, Math.atan2(p2b2.y - place1.y, p2b2.x - place1.x), Math.atan2(p1b.y - place1.y, p1b.x - place1.x), 20);
  const arcPoints2 = calculateArcPoints({ x: place2.x, y: place2.y }, place2.radius, Math.atan2(p2b.y - place2.y, p2b.x - place2.x), Math.atan2(p1a2.y - place2.y, p1a2.x - place2.x), 20);

  const polygon = [
    ...arcPoints1.map(point => ({ x: parseFloat(point.x.toFixed(1)), y: parseFloat(point.y.toFixed(1)) })),
    ...arcPoints2.map(point => ({ x: parseFloat(point.x.toFixed(1)), y: parseFloat(point.y.toFixed(1)) })),
  ];

  // If the circles overlap, the intersection point will be null
  // and we should not add it to the polygon
  if (intersectionPoint !== null) {
    polygon.push({ x: parseFloat(intersectionPoint.x.toFixed(1)), y: parseFloat(intersectionPoint.y.toFixed(1)) });
  }

  // Ensure the polygon is closed
  polygon.push(polygon[0]);


  return polygon;
}
