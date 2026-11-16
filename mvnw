#!/usr/bin/env sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
if command -v mvn >/dev/null 2>&1; then exec mvn "$@"; fi
version=3.9.11
base="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$version"
mkdir -p .tools
archive="apache-maven-$version-bin.tar.gz"
if [ ! -x ".tools/apache-maven-$version/bin/mvn" ]; then
  curl -fLSs "$base/$archive" -o ".tools/$archive"
  curl -fLSs "$base/$archive.sha512" -o ".tools/$archive.sha512"
  expected="$(cat ".tools/$archive.sha512")"
  printf '%s  %s\n' "$expected" ".tools/$archive" | sha512sum -c -
  tar -xzf ".tools/$archive" -C .tools
fi
exec ".tools/apache-maven-$version/bin/mvn" "$@"
