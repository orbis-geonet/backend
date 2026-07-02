self.onmessage = function(event) {
  const { imgData, options, type } = event.data;
  const colorCounts = {};

  for (let i = 0; i < imgData.length; i += 4) {
      const r = imgData[i];
      const g = imgData[i + 1];
      const b = imgData[i + 2];
      const a = imgData[i + 3];

      if (a === 0) continue;

      const color = `${r},${g},${b}`;

      if (options.excludeColors.some(excludeColor => color === excludeColor)) continue;

      const roundedColor = [
          Math.round(r / options.threshold) * options.threshold,
          Math.round(g / options.threshold) * options.threshold,
          Math.round(b / options.threshold) * options.threshold
      ].join(',');

      if (!colorCounts[roundedColor]) colorCounts[roundedColor] = 0;
      colorCounts[roundedColor]++;
  }

  if (!Object.keys(colorCounts).length) {
      self.postMessage({ type, color: [255, 255, 255] });
      return;
  }

  const sortedColors = Object.keys(colorCounts).sort((a, b) => colorCounts[b] - colorCounts[a]);
  const mostCommonColor = sortedColors[0].split(',').map(Number);

  self.postMessage({ type, color: mostCommonColor });
};
