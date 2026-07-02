/**
 * Calculate the points to build the external tangents of two circles.
 * @param {*} x1 x-coordinate of the center of the first circle
 * @param {*} y1 y-coordinate of the center of the first circle
 * @param {*} r1 radius of the first circle
 * @param {*} x2 x-coordinate of the center of the second circle
 * @param {*} y2 y-coordinate of the center of the second circle
 * @param {*} r2 radius of the second circle
 * @returns An object with the points of the external tangents (p1a, p1b, p2a, p2b)
 */
export function calculateExternalTangents(x1, y1, r1, x2, y2, r2) {
  // Calculate the distance between the centers of the circles
  let distCenters = Math.sqrt((x2 - x1) ** 2 + (y2 - y1) ** 2);

  // Calculate the radius of the auxiliary circle inside c1
  let r3 = r1 - r2;
  
  // Calculate the angle from the x-axis to the line connecting the centers of the circles
  let angle = Math.atan2(y2 - y1, x2 - x1);
  
  // Calculate the angle of the tangent lines
  let tangentAngle = Math.acos(r3 / distCenters);
  
  // Calculate the tangent points on circle c1
  let t1x = x1 + r1 * Math.cos(angle + tangentAngle);
  let t1y = y1 + r1 * Math.sin(angle + tangentAngle);
  let t2x = x1 + r1 * Math.cos(angle - tangentAngle);
  let t2y = y1 + r1 * Math.sin(angle - tangentAngle);
  
  // Calculate the tangent points on circle c2
  let s1x = x2 + r2 * Math.cos(angle + tangentAngle);
  let s1y = y2 + r2 * Math.sin(angle + tangentAngle);
  let s2x = x2 + r2 * Math.cos(angle - tangentAngle);
  let s2y = y2 + r2 * Math.sin(angle - tangentAngle);
  
  return {
    p1a: { x: t1x, y: t1y },
    p1b: { x: t2x, y: t2y },
    p2a: { x: s1x, y: s1y },
    p2b: { x: s2x, y: s2y },
  }
}