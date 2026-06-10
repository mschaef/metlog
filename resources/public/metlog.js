/* metlog.js */

console.log('=== metlog ===');

import { clearCache, visit } from './turbo-7.1.0.js';
import { addPollSeries, updateSeriesData, getRegisteredSeriesNames } from './metlog-data.js';
import { canvasSeriesDefn, setupPlotCanvases, updatePlots } from './metlog-plot.js';

/*** Navigation ***/

function visitPage(target) {
    clearCache();
    visit(target);
}

function refreshPage() {
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

/*** Query Window ***/

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

function setQueryWindow(newQueryWindow) {
    const parsed = parseQueryWindow(newQueryWindow);

    if (!parsed) {
        return false;
    }

    const url = new URL(window.location.href);

    url.searchParams.set('qw', newQueryWindow);
    window.history.replaceState(null, '', url);
    queryWindowMsec = parsed;

    const endT = Date.now();
    updatePlots(endT - queryWindowMsec, endT);

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

/*** Poll ***/

function updatePollData() {
    const now = Date.now();

    const displaySeries = new Set(dashboard.map((plot) => plot['series-name']));

    for(const seriesName of getRegisteredSeriesNames()) {
        if (!displaySeries.has(seriesName)) {
            continue;
        }

        updateSeriesData(seriesName, now - queryWindowMsec, now);
    }
}

/*** Initialization ***/

function initializePlots() {
    setupPlotCanvases();
    setupQueryWindow();

    document.querySelectorAll(".series-plot").forEach((canvas) => {
        addPollSeries(canvasSeriesDefn(canvas));
    });

    updatePollData();
    const endT = Date.now();
    updatePlots(endT - queryWindowMsec, endT);
}

window.addEventListener('resize', () => {
    setupPlotCanvases();
    const endT = Date.now();
    updatePlots(endT - queryWindowMsec, endT);
});

document.addEventListener("turbo:render", initializePlots);

window.addEventListener('DOMContentLoaded', () => {
    initializePlots();

    setInterval(updatePollData, 10000);
    setInterval(() => {
        const endT = Date.now();
        updatePlots(endT - queryWindowMsec, endT);
    }, 1000);
});

/*** Dashboard Actions ***/

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
    const displayRelative = formData.get("display-relative") === "Y";
    const forceZero = formData.get("force-zero") === "Y";
    const base2YAxis = formData.get("base-2-y-axis") === "Y";
    const intYAxis = formData.get("int-y-axis") === "Y";
    const drawPoints = formData.get("draw-points") === "Y";

    doPost(window.location.pathname, {
        "new-definition": JSON.stringify(dashboard.concat(({
            "series-name": seriesName,
            "display-relative": displayRelative,
            "force-zero": forceZero,
            "base-2-y-axis": base2YAxis,
            "int-y-axis": intYAxis,
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
