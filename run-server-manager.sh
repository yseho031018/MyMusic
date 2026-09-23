#!/bin/bash
cd "$(dirname "$0")"

mkdir -p target/classes
javac -encoding UTF-8 -d target/classes tools/ServerManager.java

echo "=========================================================="
echo "  My Music Server Manager (Linux Headless Daemon)"
echo "  Remote Control Port: 8088"
echo "=========================================================="
java -cp target/classes tools.ServerManager --headless

