#!/bin/sh

METLOG_SERVICE_NAME=metlog-vault
METLOG_USER_NAME=${METLOG_SERVICE_NAME}

# Must be root

if [ "$(id -u)" != "0" ]; then
   echo "This script must be run as root" 1>&2
   exit 1
fi

# create user and group

egrep "^${METLOG_USER_NAME}" /etc/passwd >/dev/null

if [ $? -eq 0 ]; then
    echo "User exists: ${METLOG_USER_NAME}"
else
    echo "Creating user: ${METLOG_USER_NAME}"

    useradd --user-group --system ${METLOG_USER_NAME}

    if [ $? -ne 0 ]; then
        echo "Could not create user: ${METLOG_USER_NAME}"
        exit 1
    fi
fi

# Install jar in /usr/share

install -v --group=root --owner=root --directory /usr/share/${METLOG_SERVICE_NAME}
install -v --group=root --owner=root lib/uberjar/${METLOG_SERVICE_NAME}-standalone.jar /usr/share/${METLOG_SERVICE_NAME}

# Create data and log directories

install -v --group=${METLOG_SERVICE_NAME} --owner=${METLOG_SERVICE_NAME} --directory /var/log/${METLOG_SERVICE_NAME}
install -v --group=${METLOG_SERVICE_NAME} --owner=${METLOG_SERVICE_NAME} --directory /var/lib/${METLOG_SERVICE_NAME}

# metlog service configuration

install -v --group=root --owner=root ${METLOG_SERVICE_NAME} /etc/init.d

update-rc.d ${METLOG_SERVICE_NAME} defaults

