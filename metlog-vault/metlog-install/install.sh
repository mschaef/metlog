#!/bin/sh

METLOG_USERNAME=metlog

# Must be root

if [ "$(id -u)" != "0" ]; then
   echo "This script must be run as root" 1>&2
   exit 1
fi

# create user and group

egrep "^${METLOG_USERNAME}" /etc/passwd >/dev/null

if [ $? -eq 0 ]; then
    echo "User exists: ${METLOG_USERNAME}"
else
    echo "Creating user: ${METLOG_USERNAME}"
    
    useradd --user-group --system ${METLOG_USERNAME}

    if [ $? -ne 0 ]; then
        echo "Could not create user: ${METLOG_USERNAME}"
        exit 1
    fi
fi

# Install jar in /usr/share

install -v --group=root --owner=root --directory /usr/share/metlog
install -v --group=root --owner=root metlog-vault-standalone.jar /usr/share/metlog

# create log directory

install -v --group=metlog --owner=metlog --directory /var/log/metlog

# create data directory

install -v --group=metlog --owner=metlog --directory /var/lib/metlog

# Configuration Files

install -v --group=root --owner=root --directory /etc/metlog
install -v --group=root --owner=root logback.xml /etc/metlog

# metlog service configuration

install -v --group=root --owner=root metlog /etc/init.d

update-rc.d metlog defaults

