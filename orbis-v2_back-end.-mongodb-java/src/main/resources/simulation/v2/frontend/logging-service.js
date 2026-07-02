export class LogService {
  logContainer = document.getElementById("infobox");

  static instance;

  constructor() {
    if (LogService.instance) {
      return LogService.instance;
    }

    LogService.instance = this;
  }

  static getInstance() {
    return LogService.instance || new LogService();
  }

  log(message) {
    const logEntry = document.createElement("p");
    logEntry.textContent = message || "\u00A0";

    this.logContainer.appendChild(logEntry);
    this.scrollToBottom();
  }

  scrollToBottom() {
    this.logContainer.scrollTop = this.logContainer.scrollHeight;
  }
}
