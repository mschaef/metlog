#!/bin/sh

if [ -z "$1" ] ; then
   echo "No Release Level Specified"
   exit 1
fi

if [ "$1" != "major" ] && [ "$1" != "minor" ] && [ "$1" != "patch" ] && \
   [ "$1" != "alpha" ] && [ "$1" != "beta" ] && [ "$1" != "rc" ] ; then

    echo "Release level \"$1\" must be one of :major , :minor , :patch , :alpha , :beta , or :rc."
    exit 1
fi

echo "Releasing level: $1"

lein clean && lein release $1

if [ $? -ne 0 ]
then
    echo "Build failed."
    exit 1
fi

