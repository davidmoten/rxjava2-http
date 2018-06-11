#!/bin/bash
set -e
mvn site
cd ../davidmoten.github.io
git pull
mkdir -p rxjava2-http 
cp -r ../rxjava2-http/target/site/* rxjava2-http/
git add .
git commit -am "update site reports"
git push
