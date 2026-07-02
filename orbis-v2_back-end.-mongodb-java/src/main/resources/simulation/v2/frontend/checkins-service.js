import { TimeService } from "./time-service.js";
import { CanvasService } from "./canvas-service.js";

import { checkin, createPlace, getPlaceByPosition } from "../business-logic/index.js";

export class CheckinsService {
  static instance;

  constructor() {
    if (CheckinsService.instance) {
      return CheckinsService.instance;
    }

    this.isActive = false;
    this.checkinsPerHour = 50;
    this.checkinsLeft = 0;

    this.initCheckinDomElements();

    CheckinsService.instance = this;
  }

  static getInstance() {
    return CheckinsService.instance || new CheckinsService();
  }

  /**
   * Initializes checkinsPerHour input field and start/stop checkins buttons.
   */
  initCheckinDomElements() {
    const checkinsPerHourInput = document.getElementById('checkinsPerHour');
    const startCheckinsButton = document.getElementById('startCheckins');
    const stopCheckinsButton = document.getElementById('stopCheckins');

    startCheckinsButton.addEventListener('click', () => {
      this.isActive = true;
    });

    stopCheckinsButton.addEventListener('click', () => {
      this.isActive = false;
    });

    checkinsPerHourInput.addEventListener('input', () => {
      this.checkinsPerHour = parseInt(checkinsPerHourInput.value);
    });

    checkinsPerHourInput.value = this.checkinsPerHour;
  }

  /**
   * If the checkins module is active, it will generate the necessary amount
   * of checkins, so that the amount of checkins per hour is reached.
   */
  async update() {
    if (!this.isActive || isNaN(this.checkinsPerHour) || this.checkinsPerHour <= 0) {
      return;
    }

    const currentTime = TimeService.getInstance().currentTime;
    const deltaTime = TimeService.getInstance().deltaTime;

    const canvasWidth = CanvasService.getInstance().canvasWidth;
    const canvasHeight = CanvasService.getInstance().canvasHeight;


    const totalAmountOfCheckins = deltaTime / (1000 * 60 * 60) * this.checkinsPerHour + this.checkinsLeft;
    this.checkinsLeft = 0;

    let amountOfCheckins = Math.floor(totalAmountOfCheckins);

    if (amountOfCheckins >= 1) {
      for (let i = 0; i < amountOfCheckins; i++) {
        const x = Math.round(Math.random() * canvasWidth);
        const y = Math.round(Math.random() * canvasHeight);

        const worldCoords = CanvasService.getInstance().canvasToWorldCoordinates(x, y);

        const place = getPlaceByPosition({ x: worldCoords.x, y: worldCoords.y });

        if (place) {
          checkin({ placeId: place.id, currentTime });
        } else {
          createPlace({ x: worldCoords.x, y: worldCoords.y, currentTime });
        }

        amountOfCheckins--;
      }
    }

    this.checkinsLeft += totalAmountOfCheckins - Math.floor(totalAmountOfCheckins);
  }
}
