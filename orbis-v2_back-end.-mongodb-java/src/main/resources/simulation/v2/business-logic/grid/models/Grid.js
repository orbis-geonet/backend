import { CanvasService } from '../../../frontend/canvas-service.js';
import { handleGetPlaces } from '../../places/handlers/handle-get-places.js';

export class GridModule {
  gridSize = 500;
  grid = [];

  static instance;

  constructor() {
    if (GridModule.instance) {
      return GridModule.instance;
    }

    GridModule.instance = this;
  }

  static getInstance() {
    if (!GridModule.instance) {
      GridModule.instance = new GridModule();
    }

    return GridModule.instance;
  }

  initGrid() {
    const scalingFactor = CanvasService.getInstance().scalingFactor;

    const canvasWidth = CanvasService.getInstance().canvasWidth;
    const canvasHeight = CanvasService.getInstance().canvasHeight;

    this.gridSize = 500;

    const numberOfRows = canvasHeight * scalingFactor / this.gridSize;
    const numberOfColumns = canvasWidth * scalingFactor / this.gridSize;

    this.grid = [];

    for (let i = 0; i < numberOfRows; i++) {
      this.grid[i] = [];

      for (let j = 0; j < numberOfColumns; j++) {
        this.grid[i][j] = [];
      }
    }

    const places = handleGetPlaces();

    for (let i = 0; i < places.length; i++) {
      const place = places[i];

      const row = Math.floor(place.y / this.gridSize);
      const column = Math.floor(place.x / this.gridSize);

      this.grid[row][column].push(place);
    }
  }

  addPlaceToGrid(place) {
    const row = Math.floor(place.y / this.gridSize);
    const column = Math.floor(place.x / this.gridSize);

    this.grid[row][column].push(place);
  }

  removePlaceFromGrid(place) {
    const row = Math.floor(place.y / this.gridSize);
    const column = Math.floor(place.x / this.gridSize);

    const index = this.grid[row][column].indexOf(place);

    if (index !== -1) {
      this.grid[row][column].splice(index, 1);
    }
  }

  getPlacesInGridCell({ x, y }) {
    const row = Math.floor(y / this.gridSize);
    const column = Math.floor(x / this.gridSize);

    return this.grid[row][column];
  }

  getPlacesInGridCells({ x, y, numberOfSurroundingCells = 5 }) {
    const row = Math.floor(y / this.gridSize);
    const column = Math.floor(x / this.gridSize);

    const places = [];

    for (let i = row - numberOfSurroundingCells; i <= row + numberOfSurroundingCells; i++) {
      for (let j = column - numberOfSurroundingCells; j <= column + numberOfSurroundingCells; j++) {
        if (i >= 0 && i < this.grid.length && j >= 0 && j < this.grid[i].length) {
          places.push(...this.grid[i][j]);
        }
      }
    }

    return places;
  }
}
