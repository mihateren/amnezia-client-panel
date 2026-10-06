#!/bin/sh
set -eu
umask 077
# Compose file secrets may be mounted world-readable; OpenSSH requires a private copy.
mkdir -p /tmp/panel-ssh
cp "${SSH_PRIVATE_KEY:-/run/secrets/ssh_key}" /tmp/panel-ssh/id_key
chmod 600 /tmp/panel-ssh/id_key
export SSH_PRIVATE_KEY=/tmp/panel-ssh/id_key
exec java --enable-native-access=ALL-UNNAMED -jar /app/app.jar "$@"

