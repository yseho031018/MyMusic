#!/bin/bash
set -e
cd "$(dirname "$0")"

mkdir -p target/server-manager-classes
javac -encoding UTF-8 -d target/server-manager-classes tools/ServerManager.java

echo "=========================================================="
echo "  My Music Server Manager (Linux Headless Daemon)"
echo "  Remote Control Port: 8088"
echo "=========================================================="
java -cp target/server-manager-classes tools.ServerManager --headless
