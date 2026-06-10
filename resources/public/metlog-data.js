/* metlog-data.js */

/*** Series Data Cache ***/

const seriesData = {};
const updatingSeries = {};

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

export function addPollSeries(seriesDefn) {
    const seriesName = seriesDefn.seriesName;

    if (seriesData[seriesName]) {
        return;
    }

    seriesData[seriesName] = emptySeriesData();
}

function fetchSeriesData(seriesName, beginT, endT) {
    return fetch('/data/' + seriesName + "?" + new URLSearchParams({
        'begin-t' : beginT,
        'end-t' : endT
    })).then((response) => response.json())
        .then((samples) => {
            return { samples, beginT, endT };
        });
}

function latestSampleTime(series) {
    if (series.samples.length > 0) {
        return series.samples[series.samples.length - 1].t;
    } else {
        return Date.now();
    }
}

export function updateSeriesData(seriesName, queryBeginT, queryEndT) {
    const series = seriesData[seriesName];

    if (!series || updatingSeries[seriesName]) {
        return;
    }

    // Empty series or series where we don't have old enough samples
    // get completely replaced.
    if (series.beginT > series.endT || queryBeginT < series.beginT)  {
        updatingSeries[seriesName] = true;
        fetchSeriesData(seriesName, queryBeginT, queryEndT)
            .then((series) => {
                seriesData[seriesName] = series;
            })
            .finally(() => {
                updatingSeries[seriesName] = false;
            });

    } else {
        const latestKnownT = Math.min(latestSampleTime(series), series.endT);

        if (queryEndT > latestKnownT) {
            updatingSeries[seriesName] = true;
            fetchSeriesData(seriesName, latestKnownT, queryEndT)
                .then((update) => {
                    seriesData[seriesName] = combineSeriesData(seriesData[seriesName], update);
                })
                .finally(() => {
                    updatingSeries[seriesName] = false;
                });
        }
    }
}

export function getSeriesData(seriesName) {
    return seriesData[seriesName];
}

export function getRegisteredSeriesNames() {
    return Object.keys(seriesData);
}
