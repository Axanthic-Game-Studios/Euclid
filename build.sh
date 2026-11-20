#!/usr/bin/env bash
# Builds Euclid-<version>.jar, the version is read from Euclid.VERSION.
# Uses the JDK from JAVA_HOME if set, otherwise javac and jar from the PATH.
set -euo pipefail
cd "$(dirname "$0")"

version=$(sed -n 's/.*String VERSION = "\([^"]*\)".*/\1/p' src/euclid/Euclid.java)
if [ -z "$version" ]; then
	echo "VERSION not found in src/euclid/Euclid.java" >&2
	exit 1
fi

bin=""
if [ -n "${JAVA_HOME:-}" ]; then
	bin="$(cygpath -u "$JAVA_HOME")/bin/"
fi
echo "Using $("${bin}javac" -version 2>&1) from ${bin:-PATH}"

rm -rf out/jar
"${bin}javac" --release 25 -d out/jar $(find src -name "*.java")
cp -r resources/fonts out/jar/
"${bin}jar" --create --file "Euclid-$version.jar" --main-class euclid.Euclid -C out/jar .
echo "Built Euclid-$version.jar"
