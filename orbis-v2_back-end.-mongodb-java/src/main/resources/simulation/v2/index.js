import { CanvasService } from "./frontend/canvas-service.js";
import { TimeService } from "./frontend/time-service.js";
import { CheckinsService } from "./frontend/checkins-service.js";
import { initBackend, updateBackend } from "./business-logic/index.js";

initBackend();
function update() {
  if (TimeService.getInstance().timeIsRunning) {
    // Update time
    TimeService.getInstance().updateCurrentTime();
    TimeService.getInstance().updateDateTime();

    // Update checkins
    CheckinsService.getInstance().update();
  }

  draw();
}

setInterval(updateBackend, 1000);

const showTripwires = document.getElementById('showTripwires');

function draw() {
  CanvasService.getInstance().clearCanvas();

  CanvasService.getInstance().drawPlaces();

  if (showTripwires.checked) {
    CanvasService.getInstance().drawTripwires();
  }

  CanvasService.getInstance().drawPolygons();
  
  requestAnimationFrame(update);
}

draw();
