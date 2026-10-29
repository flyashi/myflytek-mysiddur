#!/usr/bin/env bash
# Download the Ashkenaz pages, Tehillim and the Torah readings from onlinesiddur.com
# into source/ (gitignored). tools/import.py turns them into content/.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p source
BASE=https://onlinesiddur.com
PAGES="shacharit mincha maariv brachot birkat bracha ksham kadish derech musaf musafmoed
       hoshanot nerot megillah omer levana slichot tishabav milah klali chatzot"
for p in $PAGES; do
  echo ">> $p"; curl -fsSL --retry 3 -o "source/$p.html" "$BASE/$p/ashkenaz/index.html"
done
for p in tehilim torah; do
  echo ">> $p"; curl -fsSL --retry 3 -o "source/$p.html" "$BASE/$p/index.html"
done
date -u +%Y-%m-%d > source/fetched.txt
echo "fetched $(ls source/*.html | wc -l) pages"
