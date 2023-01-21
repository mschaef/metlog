/* metlog.js */

function seconds(seconds) { return seconds * 1000; }
function minutes(minutes) { return seconds(minutes * 60); }
function hours(hours) { return minutes(hours * 60); }
function days(days) { return hours(days * 24); }
function weeks(weeks) { return days(weeks * 7); }

var queryWindowMsec = days(1);

function parseQueryWindow(text) {
    text = text.trim();

    if (text.length < 2) {
        return false;
    }

    const windowUnitChar = text[text.length - 1];
    const windowValue = parseInt(text.substring(0, text.length - 1));

    if (isNaN(windowValue)) {
        return false;
    }

    switch(windowUnitChar) {
    case "s": return seconds(windowValue);
    case "m": return minutes(windowValue);
    case "h": return hours(windowValue);
    case "d": return days(windowValue);
    case "w": return weeks(windowValue);
    default: return false;
    }
}

function foreach_elem(selector, fn) {
    Array.prototype.forEach.call(document.querySelectorAll(selector), function(el, i) { fn(el); });
}

/*** Series Data ***/

let seriesPolls = {};

function addPollSeries(seriesName) {
    seriesPolls[seriesName] = seriesPolls[seriesName] || [];
}

function fetchSeriesData(seriesName, beginT, endT) {
    fetch('/data/' + seriesName + "?" + new URLSearchParams({
        'begin-t' : beginT,
        'end-t' : endT
    })).then((response) => response.json())
        .then((data) => seriesPolls[seriesName] = data);
}

function updatePollData() {
    const now = Date.now();

    for(var seriesName in seriesPolls) {
        fetchSeriesData(seriesName, now - queryWindowMsec, now);
    }
}

/*** Plot ***/

function pixelSnap(t) {
    return 0.5 + Math.floor(t);
}

function makeNumericIntervals(minMag, maxMag, scales) {
    const intervals = [];

    for(var mag = minMag; mag <= maxMag; mag++) {
        for(const scale of scales) {
            intervals.push(Math.pow(10, mag) * scale);
        }
    }

    return intervals;
}

const X_AXIS_SPACE = 20;
const TSPLOT_RIGHT_MARGIN = 5;
const Y_AXIS_SPACE = 50;

const PIXELS_PER_X_LABEL = 100;
const PIXELS_PER_Y_LABEL = 20;

const Y_INTERVALS = makeNumericIntervals(-12, 13, [1, 2, 5]);

const X_INTERVALS = [
    seconds(1), seconds(2), seconds(5), seconds(10), seconds(15), seconds(30),
    minutes(1), minutes(2), minutes(5), minutes(10), minutes(15), minutes(30),
    hours(1), hours(2), hours(3), hours(6), hours(12),
    days(1), days(2), days(3), days(7), days(14), days(30), days(90), days(180),
    days(360),
];

function preserveContext(ctx, draw) {
    ctx.save();
    try {
        draw();
    } finally {
        ctx.restore();
    }
}

function rangeContainsZero(range) {
    return range.min < 0.0 && range.max > 0.0;
}

function rangeMagnitude(range) {
    return range.max - range.min;
}

function range(min, max) {
    return { min: min, max: max };
}

function setStrokeGrid(ctx, emphasis) {
    ctx.lineWidth = 0;
    if (emphasis) {
        ctx.strokeStyle = "#000000";
        ctx.setLineDash([]);
    } else {
        ctx.strokeStyle = "#707070";
        ctx.setLineDash([1, 1]);
    }
}

function setStrokeSeriesLine(ctx) {
    ctx.lineWidth = 0;
    ctx.strokeStyle = "#0000FF";
    ctx.setLineDash([]);
}

function setStrokeFrame(ctx) {
    ctx.lineWidth = 1;
    ctx.strokeStyle = "#000000";
    ctx.setLineDash([]);
}

function clipRect(ctx, x, y, w, h) {
    ctx.beginPath();
    ctx.rect(x, y, w, h);
    ctx.clip();
}

function drawLine(ctx, x1, y1, x2, y2) {
    ctx.beginPath();
    ctx.moveTo(x1, y1);
    ctx.lineTo(x2, y2);
    ctx.stroke();
}

function drawFrame(ctx, w, h) {
    preserveContext(ctx, () => {
        setStrokeFrame(ctx);
        drawLine(ctx, 0.5, 0.5, 0.5, h - 0.5);
    });
}

function drawSeriesBackground(ctx, w, h) {
    preserveContext(ctx, () => {
        ctx.fillStyle = "#FFFFFF";
        ctx.fillRect(0, 0, w, h);
    });
}

function dataYRange(data, factor) {
    var maxV = data[0].val;
    var minV = data[0].val;

    for(var ii = 0; ii < data.length; ii++) {
        maxV = Math.max(data[ii].val, maxV);
        minV = Math.min(data[ii].val, minV);
    }

    const magnitude = Math.max(maxV - minV, factor);
    const delta = ((factor - 1) / 2) * magnitude;

    return range(minV - delta, maxV + delta);
}

function restrictData(data, beginT, endT) {
    return data.filter((sample) => {
        return (sample.t >= beginT && sample.t < endT);
    });
}

function translateFn(fromRange, toMax, flipped) {
    return (x) => {
        const scaled = toMax * ((x - fromRange.min) / (fromRange.max - fromRange.min));

        return flipped ? (toMax - scaled) : scaled;
    };
}

function drawSeriesLine(ctx, data, xRange, yRange, w, h) {
    const tx = translateFn(xRange, w);
    const ty = translateFn(yRange, h, true);

    setStrokeSeriesLine(ctx);

    ctx.beginPath();
    ctx.moveTo(tx(data[0].t), ty(data[0].val));
    for(var ii = 1; ii < data.length; ii++) {
        ctx.lineTo(tx(data[ii].t), ty(data[ii].val));
    }
    ctx.stroke();
}

function drawXLabel(ctx, text, x, y) {
    preserveContext(ctx, () => {
        ctx.textBaseline = "top";
        ctx.textAlign = "center";
        ctx.fillText(text, x, y);
    });
}

function formatXLabel(val) {
    const date = new Date(val);

    const MM = date.getMonth() + 1;
    const dd = date.getDate();
    const hh = date.getHours();
    const mm = date.getMinutes().toString().padStart(2, '0');

    return MM + "-" + dd + " " + hh + ":" + mm;
}

function formatYLabel(val) {
    var suffix = "";
    const mag = Math.abs(val);

    if (mag > 1000000000) {
        suffix = "G";
        val = val / 1000000000;
    } else if (mag > 1000000) {
        suffix = "M";
        val = val / 1000000;
    } else if (mag > 1000) {
        suffix = "K";
        val = val / 1000;
    }

    return val.toFixed(1) + suffix;
}

function largestYRangeMagnitude(yRange) {
    if (rangeContainsZero(yRange)) {
        if (yRange.max > Math.abs(yRange.min)) {
            return {
                t: yRange.max / rangeMagnitude(yRange),
                mag: yRange.max
            };
        } else {
            return {
                t: Math.abs(yRange.min / rangeMagnitude(yRange)),
                mag: -yRange.min
            };
        }
    } else {
        return {
            t: 1.0,
            mag: rangeMagnitude(yRange)
        };
    }
}

function findYGridInterval(h, yRange) {
    const { t, mag } = largestYRangeMagnitude(yRange);
    const availPixels = h * t;

    for(var ii = 0; ii < Y_INTERVALS.length; ii++) {
        if (PIXELS_PER_Y_LABEL * (mag / Y_INTERVALS[ii]) < availPixels)  {
            return Y_INTERVALS[ii];
        }
    }

    return Y_INTERVALS[Y_INTERVALS.length - 1];
}

function drawXGridLine(ctx, h, x, value) {
    preserveContext(ctx, () => {
        drawXLabel(ctx, formatXLabel(value), x, h);
        setStrokeGrid(ctx, false);
        drawLine(ctx, x, 0, x, h);
    });
}

function drawYLabel(ctx, text, x, y) {
    preserveContext(ctx, () => {
        ctx.textBaseline = "middle";
        ctx.textAlign = "right";
        ctx.fillText(text, x, y);
    });
}

function drawYGridLine(ctx, w, y, value, emphasis) {
    preserveContext(ctx, () => {
        drawYLabel(ctx, formatYLabel(value), -2, y);
        setStrokeGrid(ctx, emphasis);
        drawLine(ctx, 0, y, w, y);
    });
}

function findXGridInterval(w, range) {
    const mag = rangeMagnitude(range);
    const maxLabelCount = Math.floor(w / PIXELS_PER_X_LABEL);

    for(var ii = 0; ii < X_INTERVALS.length; ii++) {
        if (mag / X_INTERVALS[ii] < maxLabelCount)  {
            return X_INTERVALS[ii];
        }
    }

    return X_INTERVALS[X_INTERVALS.length - 1];
}
function drawXGrid(ctx, w, h, xRange) {
    const xInterval = findXGridInterval(w, xRange);
    const tx = translateFn(xRange, w);

    const maxX = Math.floor(xRange.max / xInterval) * xInterval;

    const gridLines = rangeMagnitude(xRange) / xInterval;

    for(var ii = 0; ii < gridLines; ii++) {
        const x = maxX - ii * xInterval;
        drawXGridLine(ctx, h, tx(x), x);
    }
}

function drawYGrid(ctx, w, h, yRange) {
    const yInterval = findYGridInterval(h, yRange);
    const ty = translateFn(yRange, h, true);

    let lineYs = [];

    if (rangeContainsZero(yRange)) {
        lineYs.push(0);

        var y = yInterval;
        while(y < yRange.max) {
            lineYs.push(y);
            y += yInterval;
        }

        y = -yInterval;
        while(y > yRange.min) {
            lineYs.push(y);
            y -= yInterval;
        }
    } else {
        var y = yRange.min;
        while(y < yRange.max) {
            lineYs.push(y);
            y += yInterval;
        }
    }

    for(var gy of lineYs) {
        drawYGridLine(ctx, w, pixelSnap(ty(gy)), gy, gy == 0);
    }
}

function drawSeries(ctx, w, h, seriesName, beginT, endT) {
    const data = restrictData(seriesPolls[seriesName] || [], beginT, endT);

    if (!data.length) {
        return;
    }

    const yRange = dataYRange(data, 1.1);

    preserveContext(ctx, () => {
        ctx.font = "12px Arial";
        drawXGrid(ctx, w, h, range(beginT, endT));
        drawYGrid(ctx, w, h, yRange);
        clipRect(ctx, 0, 0, w, h);
        drawSeriesLine(ctx, data, range(beginT, endT), yRange, w, h);
    });
}

function drawPlot(ctx, w, h, seriesName, beginT, endT) {
    const pw = w - Y_AXIS_SPACE - TSPLOT_RIGHT_MARGIN;
    const ph = h - X_AXIS_SPACE;

    drawSeriesBackground(ctx, w, h);

    preserveContext(ctx, () => {
        ctx.translate(Y_AXIS_SPACE, 0);

        drawSeries(ctx, pw, ph, seriesName, beginT, endT);
        drawFrame(ctx, pw, ph);
    });
}

function updatePlot(canvas, seriesName, beginT, endT)  {
    const dpr = window.devicePixelRatio;
    const width = canvas.width / dpr;
    const height = canvas.height / dpr;

    const ctx = canvas.getContext("2d");

    drawPlot(ctx, width, height, seriesName, beginT, endT);
}

function setupCanvas(canvas) {
    const width = canvas.parentElement.clientWidth;
    const height = canvas.parentElement.clientHeight;

    const dpr = window.devicePixelRatio;

    canvas.width = dpr * width;
    canvas.height = dpr * height;

    canvas.style["width"] = width + "px";
    canvas.style["height"] = height + "px";

    const ctx = canvas.getContext("2d");
    ctx.scale(dpr, dpr);
}

function setupPlotCanvases() {
    foreach_elem(".series-plot", (canvas) => {
        setupCanvas(canvas);
    });
}

/*** Top Level Setup ***/

function setQueryWindow(newQueryWindow) {
    const parsed = parseQueryWindow(newQueryWindow);

    if (!parsed) {
        return false;
    }

    const url = new URL(window.location.href);

    url.searchParams.set('qw', newQueryWindow);
    window.history.replaceState(null, '', url);
    queryWindowMsec = parsed;

    updatePlots();

    return true;
}

function setupQueryWindow() {
    const el = document.getElementById("query-window");

    el.onkeydown = (event) => {
        if(event.key !== 'Enter') {
            return;
        }

        if (setQueryWindow(el.value)) {
            el.classList.toggle("uncommitted", false);
        }
    };

    el.oninput = (event) => {
        const parsed = parseQueryWindow(el.value);

        el.classList.toggle("invalid", !parsed);
        el.classList.toggle("uncommitted", true);
    };

    const urlQueryWindow = new URL(window.location.href).searchParams.get('qw');
    if (urlQueryWindow) {
        setQueryWindow(urlQueryWindow);
        el.value = urlQueryWindow;
    }
}


window.addEventListener('resize', () => {
    setupPlotCanvases();
    updatePlots();
});

function updatePlots() {
    const endT = Date.now();
    const beginT = endT - queryWindowMsec;

    foreach_elem(".series-plot", (canvas) => {
        updatePlot(canvas, canvas.dataset['series-name'], beginT, endT);
    });
}

window.addEventListener('DOMContentLoaded', () => {
    foreach_elem(".series-plot", (canvas) => {
        addPollSeries(canvas.dataset['series-name']);
    });

    setupPlotCanvases();
    setupQueryWindow();

    updatePollData();
    setInterval(updatePollData, 10000);
    setInterval(updatePlots, 1000);
});


window._metlog = {
};
