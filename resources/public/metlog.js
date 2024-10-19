/* metlog.js */

console.log('=== metlog ===');

import { clearCache, visit } from './turbo-7.1.0.js';

const EPSILON = 0.00001;

function visitPage(target)
{
    clearCache();
    visit(target);
}

function refreshPage()
{
    visitPage(location);
}

function doPost(baseUrl, args, nextUrl) {
    var queryArgs = [];

    for(const argName in args) {
        queryArgs.push((argName + '=' + args[argName]));
    }

    var url = baseUrl;

    if (queryArgs.length) {
        url += ('?' + queryArgs.join('&'));
    }

    function doRefresh(resp) {
        if (nextUrl) {
            visitPage(nextUrl);
        } else if (resp.redirected)  {
            visitPage(resp.url);
        } else {
            refreshPage();
        }
    }

    fetch(url, {
        method: 'POST',
        credentials: 'include'
    }).then(doRefresh);
}

/*** Utilities ***/

function foreach_elem(selector, fn) {
    Array.prototype.forEach.call(document.querySelectorAll(selector), function(el, i) { fn(el); });
}

/*** Query Window Parsing ***/

const MAX_DATE = new Date(8640000000000000);

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

/*** Series Data ***/

let seriesData = {};

function emptySeriesData() {
    return {
        samples: [],
        beginT: Number.MAX_SAFE_INTEGER,
        endT: Number.MIN_SAFE_INTEGER
    };
}

function combineSeriesData(sA, sB) {
    const allSamples = sA.samples.concat(sB.samples);

    const combined = allSamples.sort((a, b) => {
        if (a.t < b.t) {
            return -1;
        } else if (a.t > b.t) {
            return 1;
        }

        return 0;
    }).filter(function(item, pos, ary) {
        return !pos || item.t != ary[pos - 1].t;
    });

    return {
        samples: combined,
        beginT: Math.min(sA.beginT, sB.beginT),
        endT: Math.max(sA.endT, sB.endT),
    };
}


function addPollSeries(seriesDefn) {
    const seriesName = seriesDefn.seriesName;

    if (seriesData[seriesName]) {
        return;
    }

    seriesData[seriesName] = emptySeriesData();
}

function fetchSeriesData(seriesName, beginT, endT) {
    return fetch('/dashboard/data/' + seriesName + "?" + new URLSearchParams({
        'begin-t' : beginT,
        'end-t' : endT
    })).then((response) => response.json())
        .then((samples) => ({ samples, beginT, endT }));
}

function replaceSeriesData(seriesName, segBeginT, segEndT) {
    fetchSeriesData(seriesName, segBeginT, segEndT)
        .then(( series ) => {
            seriesData[seriesName] = series;
        });
}

function extendSeriesData(seriesName, segBeginT, segEndT) {
    if (segEndT < segBeginT) {
        return;
    }

    fetchSeriesData(seriesName, segBeginT, segEndT)
        .then(( update ) => {
            seriesData[seriesName] = combineSeriesData(seriesData[seriesName], update);
        });
}

function latestSampleTime(seriesData) {
    if (seriesData.samples.length > 0) {
        return seriesData.samples[seriesData.samples.length - 1].t;
    } else {
        return Date.now();
    }
}

function updateSeriesData(seriesName, queryBeginT, queryEndT) {
    const series = seriesData[seriesName];

    if (!series) {
        return;
    }

    if (series.beginT > series.endT   // Empty series
        || queryEndT < series.beginT  // Query fully before series
        || queryBeginT > series.endT  // Query fully after series
       ) {
        replaceSeriesData(seriesName, queryBeginT, queryEndT);
    } else {
        extendSeriesData(seriesName, queryBeginT, series.beginT);

        var endT = Math.min(latestSampleTime(series), series.endT);
        extendSeriesData(seriesName, endT, queryEndT);
    }
}

function updatePollData() {
    const now = Date.now();

    for(var seriesName in seriesData) {
        updateSeriesData(seriesName, now - queryWindowMsec, now);
    }
}

/*** Plot ***/

function pixelSnap(t) {
    return 0.5 + Math.floor(t);
}

function makeNumericIntervals(minMag, maxMag, base, scales) {
    const intervals = [];

    for(var mag = minMag; mag <= maxMag; mag++) {
        for(const scale of scales) {
            intervals.push(Math.pow(base, mag) * scale);
        }
    }

    return intervals;
}

const PLOT_Y_PADDING = 4;

const X_AXIS_SPACE = 20;
const TSPLOT_RIGHT_MARGIN = 5;
const Y_AXIS_SPACE = 60;

const PIXELS_PER_X_LABEL = 100;
const PIXELS_PER_Y_LABEL = 20;

const Y2_TICK_INTERVALS = makeNumericIntervals(0, 32, 2, [1]);
const Y10_TICK_INTERVALS = makeNumericIntervals(-12, 13, 10, [1, 2, 5]);

const X_TICK_INTERVALS = [
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

function intervalContainsZero(interval) {
    return interval.min <= 0.0 && interval.max >= 0.0;
}

function intervalMagnitude(interval) {
    return interval.max - interval.min;
}

function interval(min, max) {
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
        drawLine(ctx, 0.5, PLOT_Y_PADDING, 0.5, h - PLOT_Y_PADDING);
    });
}

function drawSeriesBackground(ctx, w, h) {
    preserveContext(ctx, () => {
        ctx.fillStyle = "#FFFFFF";
        ctx.fillRect(0, 0, w, h);
    });
}

function dataYRange(data, forceZero) {
    var maxV = data[0].val;
    var minV = data[0].val;

    for(var ii = 0; ii < data.length; ii++) {
        maxV = Math.max(data[ii].val, maxV);
        minV = Math.min(data[ii].val, minV);
    }

    if (forceZero) {
        maxV = Math.max(maxV, 0.0);
        minV = Math.min(minV, 0.0);
    }

    if (maxV - minV < EPSILON) {
        return interval(minV - EPSILON, maxV + EPSILON);
    } else {
        return interval(minV, maxV);
    }
}

function restrictData(data, beginT, endT) {
    return data.samples.filter((sample) => {
        return (sample.t >= beginT && sample.t < endT);
    });
}

function translateFn(fromRange, toMax, padding, flipped) {
    const scaleFactor = (toMax - padding * 2) / (fromRange.max - fromRange.min);

    return (x) => {
        const scaled = (x - fromRange.min) * scaleFactor;

        return flipped ? (toMax - scaled - padding) : scaled + padding;
    };
}

const POINT_DIMEN = 2;

function averageT(data) {
    const n = data.length;

    if (n > 2) {
        return (data[n - 1].t - data[0].t) / n;
    } else {
        return 0.0;
    }
}

function drawSeriesLine(ctx, data, xRange, yRange, w, h, drawPoints) {
    const tx = translateFn(xRange, w, 0);
    const ty = translateFn(yRange, h, PLOT_Y_PADDING, true);

    setStrokeSeriesLine(ctx);

    if (drawPoints) {
        for(var ii = 1; ii < data.length; ii++) {
            const xx = tx(data[ii].t);
            const yy = ty(data[ii].val);

            ctx.beginPath();
            ctx.moveTo(xx - POINT_DIMEN, yy);
            ctx.lineTo(xx + POINT_DIMEN, yy);

            ctx.moveTo(xx, yy - POINT_DIMEN);
            ctx.lineTo(xx, yy + POINT_DIMEN);
            ctx.stroke();
        }


    } else if (data.length > 0) {
        ctx.beginPath();
        ctx.moveTo(tx(data[0].t), ty(data[0].val));
        const avgT = averageT(data);
        var lastT = data[0].t;
        for(var ii = 1; ii < data.length; ii++) {
            var t = data[ii].t;

            if (t - lastT > avgT * 4) {
                ctx.moveTo(tx(t), ty(data[ii].val));
            } else {
                ctx.lineTo(tx(t), ty(data[ii].val));
            }

            lastT = t;
        }
        ctx.stroke();
    }
}

function drawXMaxLabel(ctx, text, x, y) {
    preserveContext(ctx, () => {
        ctx.font = "Bold 12px Arial";
        ctx.textBaseline = "top";
        ctx.textAlign = "right";
        ctx.fillText(text, x, y);
    });
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

function formatYLabel(val, base2) {
    var suffix = "";
    const mag = Math.abs(val);

    let precision = 0;

    if (base2) {
        if (mag >= 1024 * 1024 * 1024) {
            suffix = "Gi";
            val = val / (1024 * 1024 * 1024);
        } else if (mag >= 1024 * 1024) {
            suffix = "Mi";
            val = val / (1024 * 1024);
        } else if (mag >= 1024) {
            suffix = "Ki";
            val = val / 1024;
        }
    } else {
        if (mag >= 1000000000) {
            suffix = "G";
            val = val / 1000000000;
        } else if (mag >= 1000000) {
            suffix = "M";
            val = val / 1000000;
        } else if (mag >= 1000) {
            suffix = "K";
            val = val / 1000;
        }

        const mag2 = Math.abs(val);

        if (mag2 >= 100.0) {
            precision = 1;
        } else if (mag2 >= 10.0) {
            precision = 2;
        } else if (mag2 >= 1.0)  {
            precision = 3;
        } else if (mag2 > 0.0) {
            precision = 4;
        }
    }



    return val.toFixed(precision) + suffix;
}

function largestYRangeMagnitude(yRange) {
    if (intervalContainsZero(yRange)) {
        if (yRange.max > Math.abs(yRange.min)) {
            return {
                t: yRange.max / intervalMagnitude(yRange),
                mag: yRange.max
            };
        } else {
            return {
                t: Math.abs(yRange.min / intervalMagnitude(yRange)),
                mag: -yRange.min
            };
        }
    } else {
        return {
            t: 1.0,
            mag: intervalMagnitude(yRange)
        };
    }
}

function findYGridTickInterval(h, yRange, base2YAxis) {
    const { t, mag } = largestYRangeMagnitude(yRange);
    const availPixels = h * t;

    const yTickIntervals = base2YAxis ? Y2_TICK_INTERVALS : Y10_TICK_INTERVALS;

    for(var ii = 0; ii < yTickIntervals.length; ii++) {
        const interval = yTickIntervals[ii];

        if (PIXELS_PER_Y_LABEL * (mag / interval) < availPixels)  {
            return interval;
        }
    }

    return yTickIntervals[yTickIntervals.length - 1];
}

function drawXGridLine(ctx, h, x, value, drawLabel) {
    preserveContext(ctx, () => {
        if (drawLabel) {
            drawXLabel(ctx, formatXLabel(value), x, h);
        }
        setStrokeGrid(ctx, false);
        drawLine(ctx, x, 0, x, h);
    });
}

function drawYLabel(ctx, text, y, baseline) {
    preserveContext(ctx, () => {
        ctx.textBaseline = baseline;
        ctx.textAlign = "right";
        ctx.fillText(text, -2, y);
    });
}

function drawYGridLine(ctx, w, y, value, emphasis, base2) {
    preserveContext(ctx, () => {
        drawYLabel(ctx, formatYLabel(value, base2), y, "middle");
        setStrokeGrid(ctx, emphasis);
        drawLine(ctx, 0, y, w, y);
    });
}

function findXGridTickInterval(w, range) {
    const mag = intervalMagnitude(range);
    const maxLabelCount = Math.floor(w / PIXELS_PER_X_LABEL);

    for(var ii = 0; ii < X_TICK_INTERVALS.length; ii++) {
        if (mag / X_TICK_INTERVALS[ii] < maxLabelCount)  {
            return X_TICK_INTERVALS[ii];
        }
    }

    return X_TICK_INTERVALS[X_TICK_INTERVALS.length - 1];
}

function drawXGrid(ctx, w, h, xRange) {
    const xInterval = findXGridTickInterval(w, xRange);
    const tx = translateFn(xRange, w, 0);;

    const tzOfs = new Date().getTimezoneOffset() * 60 * 1000;

    // tzOfs used to ensure that this correctly round the displayed X
    // intervals to intervals in the display time zone. ie: a 3 hour
    // interval should display as 3:00, 6:00, 9:00, ...
    const minX = Math.floor((xRange.min + xInterval - tzOfs) / xInterval) * xInterval + tzOfs;

    const gridLines = intervalMagnitude(xRange) / xInterval;

    const maxXPos = tx(xRange.max);

    for(var ii = 0; ii < gridLines; ii++) {
        const xVal = minX + ii * xInterval;

        const x = tx(xVal);

        drawXGridLine(ctx, h, x, xVal, x < (maxXPos - PIXELS_PER_X_LABEL));
    }

    drawXMaxLabel(ctx, formatXLabel(xRange.max), maxXPos, h);
}

function drawYGrid(ctx, w, h, yRange, base2YAxis) {
    const yInterval = findYGridTickInterval(h, yRange, base2YAxis);
    const ty = translateFn(yRange, h, PLOT_Y_PADDING, true);

    let lineYs = [];

    if (intervalContainsZero(yRange)) {
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
        var y = Math.ceil(yRange.min / yInterval) * yInterval;
        while(y < yRange.max) {
            lineYs.push(y);
            y += yInterval;
        }
    }

    var needYMin = true;
    var needYMax = true;

    for(var gy of lineYs) {
        const yPos = ty(gy);

        if (yPos > 0 && yPos < h - 0) {
            needYMax = needYMax && yPos > PIXELS_PER_Y_LABEL;
            needYMin = needYMin && yPos < h - PIXELS_PER_Y_LABEL;

            drawYGridLine(ctx, w, pixelSnap(yPos), gy, gy == 0, base2YAxis);
        }
    }

    if (needYMin) {
        drawYLabel(ctx, formatYLabel(yRange.min, base2YAxis), h, "bottom");
    }

    if (needYMax) {
        drawYLabel(ctx, formatYLabel(yRange.max, base2YAxis), 0, "top");
    }
}

function drawSeries(ctx, w, h, beginT, endT, seriesDefn) {
    const series = seriesData[seriesDefn.seriesName];

    if (!series || !series.samples) {
        return;
    }

    const samples = restrictData(seriesData[seriesDefn.seriesName], beginT, endT);

    if (!samples.length) {
        return;
    }

    const yRange = dataYRange(samples, seriesDefn.forceZero);

    preserveContext(ctx, () => {
        ctx.font = "12px Arial";
        drawXGrid(ctx, w, h, interval(beginT, endT));
        drawYGrid(ctx, w, h, yRange, seriesDefn.base2YAxis);
        clipRect(ctx, 0, 0, w, h);
        drawSeriesLine(ctx, samples, interval(beginT, endT), yRange, w, h, seriesDefn.drawPoints);
    });
}

function drawPlot(ctx, w, h, beginT, endT, seriesDefn) {
    const pw = w - Y_AXIS_SPACE - TSPLOT_RIGHT_MARGIN;
    const ph = h - X_AXIS_SPACE;

    drawSeriesBackground(ctx, w, h);

    preserveContext(ctx, () => {
        ctx.translate(Y_AXIS_SPACE, 0);

        drawSeries(ctx, pw, ph, beginT, endT, seriesDefn);
        drawFrame(ctx, pw, ph);
    });
}

function canvasSeriesDefn(canvas) {
    const defn = JSON.parse(canvas.dataset.seriesDefn);

    return {
        seriesName: defn["series-name"],
        forceZero: !!defn["force-zero"],
        base2YAxis: !!defn["base-2-y-axis"],
        drawPoints: !!defn["draw-points"],
    };
}

function updatePlot(canvas, beginT, endT)  {
    const seriesDefn = canvasSeriesDefn(canvas);

    const dpr = window.devicePixelRatio;
    const width = canvas.width / dpr;
    const height = canvas.height / dpr;

    const ctx = canvas.getContext("2d");

    drawPlot(ctx, width, height, beginT, endT, seriesDefn);
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

function updatePlots() {
    const endT = Date.now();
    const beginT = endT - queryWindowMsec;

    foreach_elem(".series-plot", (canvas) => {
        updatePlot(canvas, beginT, endT);
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

function initializePlots() {
    setupPlotCanvases();
    setupQueryWindow();

    foreach_elem(".series-plot", (canvas) => {
        addPollSeries(canvasSeriesDefn(canvas));
    });

    updatePollData();
    updatePlots();
}

document.addEventListener("turbo:render", initializePlots);

window.addEventListener('DOMContentLoaded', () => {
    initializePlots();

    setInterval(updatePollData, 10000);
    setInterval(updatePlots, 1000);
});

function removeByIndex(vec, index) {
    if (index < 0) {
        return vec;
    } else {
        return vec.slice(0, index).concat(vec.slice(index + 1));
    }
}

function onDeleteSeries(index) {
    doPost(window.location.pathname, {
        "new-definition": JSON.stringify(removeByIndex(dashboard, index))
    });
}


function onAddSeries(event) {
    event.preventDefault();

    const form = event.target.form;

    const formData = new FormData(form);

    const seriesName = formData.get("series-name");
    const forceZero = formData.get("force-zero") === "Y";
    const base2YAxis = formData.get("base-2-y-axis") === "Y";
    const drawPoints = formData.get("draw-points") === "Y";

    doPost(window.location.pathname, {
        "new-definition": JSON.stringify(dashboard.concat(({
            "series-name": seriesName,
            "force-zero": forceZero,
            "base-2-y-axis": base2YAxis,
            "draw-points": drawPoints,
        })))
    });
}

function addDashboard() {
    const newDashboardName = prompt("Enter Dashboard Name");

    if (!newDashboardName) {
        return;
    }

    doPost("/dashboard", {
        "dashboard-name": newDashboardName
    });
}

function onDashboardSelectChange(event) {
    const dashboardId = event.target.value;

    var url = '/dashboard/' + dashboardId;

    const urlQueryWindow = new URL(window.location.href).searchParams.get('qw');
    if (urlQueryWindow) {
        url = url + '?qw=' + urlQueryWindow;
    }

    visitPage(url);
}

window._metlog = {
    addDashboard,
    onAddSeries,
    onDashboardSelectChange,
    onDeleteSeries,
    doPost,
};
