/**
 * Calculates the points on an arc of a circle. If the end angle is less than the start angle, the arc will be calculated in the clockwise direction.
 * @param {*} center The center of the circle (object with {x: number, y: number})
 * @param {*} radius The radius of the circle
 * @param {*} startAngle The starting angle of the arc (in radians)
 * @param {*} endAngle The ending angle of the arc (in radians)
 * @param {*} numPoints The number of points to calculate on the arc
 * @returns An array of points on the arc (array of objects with {x: number, y: number})
 */
export function calculateArcPoints(center, radius, startAngle, endAngle, numPoints) {
  var points = [];

  // Normalize angles to be within the range [0, 2π]
  startAngle = (startAngle % (2 * Math.PI) + (2 * Math.PI)) % (2 * Math.PI);
  endAngle = (endAngle % (2 * Math.PI) + (2 * Math.PI)) % (2 * Math.PI);

  if (endAngle < startAngle) {
    endAngle += 2 * Math.PI;
  }

  // Calculate angle increment
  var angleIncrement = (endAngle - startAngle) / numPoints;

  for (let i = 0; i <= numPoints; i++) {
    var angle = startAngle + (angleIncrement * i);
    var x = center.x + radius * Math.cos(angle);
    var y = center.y + radius * Math.sin(angle);
    points.push({ x: x, y: y });
  }

  return points;
}
