#!/bin/bash

echo Will write to "$2"
pwd

files=$(cd "$1"; echo *.mail)

for file in $files; do
  java -jar build/libs/LafiteToMBox-1.0-SNAPSHOT.jar --laurel "$1"/"$file" --mbox "$2"/"$file".mbox
done
