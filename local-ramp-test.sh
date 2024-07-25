#!/bin/sh

# local-ramp-test.sh
#
# This uses the /sample endpoint to push data points into a
# vault. Samples should arrive within well under a second of each
# other, serving as a test of sub-second resolution in the data
# pipeline from ingestion to UI.

for ii in $(seq 200)
do
    echo "${ii}"
    curl http://localhost:8080/sample/ramp-a \
         -X POST --header "Content-Type: application/json" --data "${ii}" --silent > /dev/null
    curl http://localhost:8080/sample/ramp-b \
         -X POST --header "Content-Type: application/json" --data "${ii}" --silent > /dev/null
done
