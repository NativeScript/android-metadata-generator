#! /bin/bash

if [ "$#" -lt "2" ]; then
    echo "No arguments supplied. Usage: $0 [path-to-jars-to-generate-metadata-for] [output-path]"
    exit
fi

if [ -d "bin" ]; then
    echo "bin folder found! In its current version the metadata generator uses a bin folder for a temporary metadata output location!"
    exit
fi

if [ ! -d $2 ]; then
    mkdir $2
fi

resolvedpath=$(readlink -f $0)
executabledir=$(dirname $resolvedpath)

mkdir bin

java -cp $executabledir/../classes com.telerik.metadata.Generator $1
if [ $? -ne 0 ]; then
    exit $?
fi

mv bin/* $2
rmdir bin
