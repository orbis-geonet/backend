function crossProduct(v1, v2) {
  return v1.x * v2.y - v1.y * v2.x;
}

export function doLineSegmentsIntersect(p1, p2, p3, p4) {
  let v1 = { x: p2.x - p1.x, y: p2.y - p1.y };
  let v2 = { x: p3.x - p1.x, y: p3.y - p1.y };
  let v3 = { x: p4.x - p1.x, y: p4.y - p1.y };

  let cross1 = crossProduct(v1, v2);
  let cross2 = crossProduct(v1, v3);

  if ((cross1 > 0 && cross2 > 0) || (cross1 < 0 && cross2 < 0)) {
      return false;
  }

  v1 = { x: p4.x - p3.x, y: p4.y - p3.y };
  v2 = { x: p1.x - p3.x, y: p1.y - p3.y };
  v3 = { x: p2.x - p3.x, y: p2.y - p3.y };

  cross1 = crossProduct(v1, v2);
  cross2 = crossProduct(v1, v3);

  if ((cross1 > 0 && cross2 > 0) || (cross1 < 0 && cross2 < 0)) {
      return false;
  }

  return true;
}
