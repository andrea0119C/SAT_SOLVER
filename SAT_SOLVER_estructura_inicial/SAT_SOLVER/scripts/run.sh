#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p out
find src/main/java -name '*.java' > out/sources.txt
javac -encoding UTF-8 -d out @out/sources.txt
java -cp out ec.edu.satsolver.Main
