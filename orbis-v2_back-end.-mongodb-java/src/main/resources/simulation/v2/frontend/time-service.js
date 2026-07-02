// Text elements to display the current year, month, day, and hour
const yearElement = document.getElementById('year');
const monthElement = document.getElementById('month');
const dayElement = document.getElementById('day');
const hourElement = document.getElementById('hour');

export class TimeService {
  static instance;

  static getInstance() {
    return TimeService.instance || new TimeService();
  }

  constructor() {
    if (TimeService.instance) {
      return TimeService.instance;
    }

    this.timeIsRunning = true;
    this.currentTime = new Date();
    this.deltaTime = 1000 * 60 * 60 * 0.02;

    this.initTimeControls();

    TimeService.instance = this;
  }

  updateCurrentTime() {
    if (this.timeIsRunning) {
      this.currentTime.setTime(this.currentTime.getTime() + this.deltaTime);
    }
  }

  initTimeControls() {
    //#region Speed control and start/stop buttons
    const speedElement = document.getElementById('speed');
    const startTimeElement = document.getElementById('startTime');
    const stopTimeElement = document.getElementById('stopTime');

    speedElement.addEventListener('input', () => {
      this.deltaTime = 1000 * 60 * 60 * 0.02 * speedElement.value
    });

    startTimeElement.addEventListener('click', () => {
      this.timeIsRunning = true;
    });

    stopTimeElement.addEventListener('click', () => {
      this.timeIsRunning = false;
    });
    //#endregion

    //#region Buttons to increase year, month, day, hour
    const increaseYearBtn = document.getElementById('increaseYear');
    const increaseMonthBtn = document.getElementById('increaseMonth');
    const increaseDayBtn = document.getElementById('increaseDay');
    const increaseHourBtn = document.getElementById('increaseHour');

    increaseYearBtn.addEventListener('click', () => {
      this.currentTime.setFullYear(this.currentTime.getFullYear() + 1);
      this.updateDateTime();
    });

    increaseMonthBtn.addEventListener('click', () => {
      this.currentTime.setMonth(this.currentTime.getMonth() + 1);
      this.updateDateTime();
    });

    increaseDayBtn.addEventListener('click', () => {
      this.currentTime.setDate(this.currentTime.getDate() + 1);
      this.updateDateTime();
    });

    increaseHourBtn.addEventListener('click', () => {
      this.currentTime.setHours(this.currentTime.getHours() + 1);
      this.updateDateTime();
    });
    //#endregion
  }

  updateDateTime() {
    yearElement.innerText = this.currentTime.getFullYear();
    monthElement.innerText = this.currentTime.getMonth() + 1;
    dayElement.innerText = this.currentTime.getDate();
    hourElement.innerText = this.currentTime.getHours();
  }
}
