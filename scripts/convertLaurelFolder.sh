#!/bin/bash

cd "$1" || exit

for file in *.mail; do
  java -jar ~/Projects/LafiteToMBox/build/libs/LafiteToMBox-1.0-SNAPSHOT.jar --laurel "$file" --mbox "$file".mbox
done
