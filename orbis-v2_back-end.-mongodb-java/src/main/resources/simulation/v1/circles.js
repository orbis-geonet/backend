const canvas = document.querySelector('#cnv');
const ctx = canvas.getContext('2d');
const currentTime = document.querySelector("#currentTime")
const clean = document.querySelector("#clean")
const regenerate = document.querySelector("#regenerate")
const start = document.querySelector("#start")
const stop = document.querySelector("#stop")
const clicks = document.querySelector("#clicks")
const infoBoxPre = document.querySelector("#infobox pre")
const infoBox = document.querySelector("#infobox")
const resistance = document.querySelector("#resistance")
const accumulatedCheckins = document.querySelector("#accumulated")

let w = canvas.clientWidth
let h = canvas.clientHeight

let minCoord = Math.min(w, h) // px
let scale = 40000.0 /* m */ / minCoord //px

const scaleToScreen = (w /* m */) => Math.round(w / scale)
const scaleToWorld = (m) => Math.round(m * scale)

const translateToScreen = (x, y) => {
    return {x: Math.round(scaleToScreen(-x) + w / 2), y: Math.round(scaleToScreen(-y) + h / 2)}
}

const translateToWorld = (x, y) => {
    return {x: -scaleToWorld(x - w / 2), y: -scaleToWorld(y - h / 2)}
}

const day = 24 * 60 * 60 * 1000
const year = 365 * day


let speed = day / 3600
let tickTime = new Date()

const bar = document.querySelector("progress")

bar.addEventListener("click", e => {
    bar.value = e.offsetX / bar.clientWidth
    speed = year * 5 / 365 * bar.value * bar.value + day / 36000
})

let grades = [500, 250, 150, 100, 50, 50, 25, 25, 17, 9, 4.5, 2.25]

function findGrade(currentSize) {
    const idx = grades.findIndex(g => g < currentSize)
    if (idx === -1) return 20;
    return idx - 1;
}

class Place {
    x;
    y;
    lastEventTime;
    lastSize;
    checkins;

    constructor(x, y, lastEventTime, lastSize) {
        this.x = x;
        this.y = y;
        this.lastEventTime = lastEventTime;
        this.lastSize = lastSize;
        this.checkins = [lastEventTime]
    }

    computeSize() {
        // ms
        const elapsedTime = (tickTime - this.lastEventTime)
        if (elapsedTime < day && this.lastSize >= 500) {
            return (this.lastSize - 500) * ((day - elapsedTime) / day) + 500;
        } else if (this.lastSize >= 500) {
            return 500 * (year - elapsedTime) / year
        } else {
            return this.lastSize * (year - elapsedTime) / year
        }
    }

    pointInside(x, y) {
        const size = this.computeSize()
        const dist = this.distanceTo(x, y)
        return dist < size
    }

    distanceTo(x, y) {
        return Math.sqrt((this.x - x) ** 2 + (this.y - y) ** 2);
    }

    touched(checkins) {
        this.lastEventTime = tickTime
        let checkinsLeft = checkins
        let currentSize = this.computeSize()

        // this is with resist
        // alternatively >= 600 clause can be removed and on any touch size of the touched can be reset to 500
        if (currentSize >= 600) {
            if (currentSize - checkinsLeft * 100 >= 500) {
                this.lastSize = currentSize - checkinsLeft * 100
                // other way to achieve no resistance is to remove following statement
                // and then grade based checkin will kick in and do the job
                // albeit when touched by a bigger circle first time can lead to unexpected results
                if(resistance.checked) {
                    checkinsLeft = 0;
                }
            } else {
                checkinsLeft -= Math.floor((currentSize - 500) / 100)
                currentSize = 500
            }
        } else if (currentSize >= 500) {
            currentSize = 500
        }

        if (checkinsLeft > 0) {
            let currentGrade = findGrade(currentSize)
            let updatedGrade = currentGrade + checkinsLeft
            if (updatedGrade < grades.length) {
                this.lastSize = grades[updatedGrade]
            } else {
                this.lastSize = 0
            }
        }
        return this.lastSize
    }
}

function clickPoint(x, y, manual) {
    let previousSize = 0

    let idx = places.findIndex(p => p.pointInside(x, y))
    if (idx >= 0) {
        previousSize = places[idx].computeSize()
        if (previousSize >= 500) {
            places[idx].lastSize = previousSize + 100
        } else {
            places[idx].lastSize = 500
        }
        if (places[idx].lastSize > 1000) {
            places[idx].lastSize = 1000
        }
        places[idx].lastEventTime = tickTime
        places[idx].checkins.push(tickTime)
        places[idx].checkins = places[idx].checkins.filter(c => tickTime - c <= day)
    } else {
        idx = places.push(new Place(x, y, tickTime, 500)) - 1;
    }

    const currentPlace = places[idx]
    const currentPlaceSize = currentPlace.computeSize()

    if (manual) {
        infoBoxPre.innerText += `\nPlace ${idx}: ${previousSize} -> ${currentPlaceSize}`
    }

    places.forEach((place, i) => {
        if (i === idx) return;
        const dist = currentPlace.distanceTo(place.x, place.y)
        const size = place.computeSize()
        // touched on this checkin

        let newSize = 0

        if (dist <= size + currentPlaceSize) {
            if((dist > size + previousSize || previousSize === 0) && accumulatedCheckins.checked) {
                newSize = place.touched(currentPlace.checkins.length)
            } else {
                newSize = place.touched(1)
            }
            if (manual) {
                infoBoxPre.innerText += `\nTouched ${i}: ${size} -> ${newSize}`

                infoBox.scrollTo(0, infoBox.scrollHeight)
            }
        }
    })
}

let places = []

function generatePlaces(number) {
    let res = []
    for (let i = 0; i < number; i++) {
        const checkins = Math.round(Math.random() * 10)
        const place = new Place((Math.random() * 40000 - 20000) * w / minCoord, (Math.random() * 40000 - 20000) * h / minCoord, tickTime,
            Math.random() * 500 + 500)
        for (let j = 0; j < checkins; j++) {
            place.checkins.push(tickTime)
        }
        res.push(place)
    }
    return res;
}

clean.addEventListener("click", () => places = [])
regenerate.addEventListener("click", () => places = generatePlaces(10000))

let clicking = 0
let lastClicked = []
let lastRealtime = new Date()

start.addEventListener("click", () => clicking = +clicks.value)
stop.addEventListener("click", () => clicking = 0)

resize()
draw()


canvas.addEventListener("click", e => {
    let {x, y} = translateToWorld(e.offsetX, e.offsetY)
    clickPoint(x, y, true);
})

let partialClicks = 0

function draw() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    places = places.filter(p => p.computeSize() > 0)

    places.forEach(place => {
        const {x, y} = translateToScreen(place.x, place.y)
        ctx.strokeStyle = "rgb(0, 0, 0)"
        ctx.lineWidth = 1
        ctx.beginPath()
        ctx.arc(x, y, scaleToScreen(place.computeSize()), 0, Math.PI * 2, true)
        ctx.stroke()
    })

    lastClicked.forEach(point => {
        ctx.strokeStyle = "rgb(255, 0, 0)"
        ctx.lineWidth = 3
        ctx.beginPath()
        ctx.moveTo(point.x - 5, point.y - 5)
        ctx.lineTo(point.x + 5, point.y + 5)
        ctx.moveTo(point.x - 5, point.y + 5)
        ctx.lineTo(point.x + 5, point.y - 5)
        ctx.stroke()
    })

    currentTime.innerHTML = tickTime.toISOString()

    if (clicking > 0) {
        partialClicks += clicking * speed / (1000 * 60 * 60)
        for (let i = 0; partialClicks >= 1 && i < 100; partialClicks--, i++) {
            if (Math.random() < 0.2 || places.length === 0) {
                let click = {x: Math.round(Math.random() * w), y: Math.round(Math.random() * h)}
                let {x, y} = translateToWorld(click.x, click.y)
                clickPoint(
                    x,
                    y,
                    false
                )
                lastClicked.push(click)
            } else {
                const idx = Math.floor(Math.random() * places.length)
                let click = translateToScreen(places[idx].x, places[idx].y)
                clickPoint(places[idx].x, places[idx].y, false)
                lastClicked.push(click)
            }
        }
        if (partialClicks > 1) partialClicks = 0
    }

    while (lastClicked.length > 20) {
        lastClicked.shift()
    }

    tickTime = new Date(tickTime.getTime() + speed)

    window.requestAnimationFrame(draw)
}

function resize() {
    // look up the size the canvas is being displayed
    const width = canvas.clientWidth;
    const height = canvas.clientHeight;

    // If it's resolution does not match change it
    if (canvas.width !== width || canvas.height !== height) {
        canvas.width = width;
        canvas.height = height;

        w = width
        h = height

        minCoord = Math.min(w, h) // px
        scale = 40000.0 /* m */ / minCoord //px

        return true;
    }

    return false;
}


const stopTime = document.querySelector("#stopTime")
const startTime = document.querySelector("#startTime")

stopTime.addEventListener("click", () => speed = 0)
startTime.addEventListener("click", () => speed = year * 5 / 365 * bar.value * bar.value + day / 36000)

const times = {
    min30: 1000*60*30,
    h1: day/24,
    h2: 2*day/24,
    h8: 8*day/24,
    h12: day/2,
    h20: 20*day/24,
    d30: 30*day,
    y05: year/2,
    y09: year*3/4
}

for (let timesKey in times) {
    const elem = document.querySelector("#" + timesKey)
    elem.addEventListener("click", () => tickTime = new Date(tickTime.getTime() + times[timesKey]))
}
