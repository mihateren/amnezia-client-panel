#!/bin/sh
set -eu
umask 077
exec java --enable-native-access=ALL-UNNAMED -jar /app/app.jar "$@"
