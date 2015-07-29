#!/usr/bin/env node

var fs = require('fs');
var path = require('path');
var child_process = require('child_process');

if (process.argv.length < 3) {
    console.error("No arguments supplied. Usage: $0 [path-to-jars-to-generate-metadata-for] [output-path]");
    process.exit(1);
}

try {
    fs.mkdirSync('bin');
} catch(e) {}

var outputFiles = fs.readdirSync('bin');

if (outputFiles.length > 0) {
    console.error("bin directory found! In its current version the metadata generator uses a bin directory for a temporary metadata output location!");
    console.log("Listing existing bin directory files");
    console.log(outputFiles.join('\n'));
    console.log("Listing existing bin directory files. end");
    process.exit(1);
}

try {
    fs.mkdirSync(path.normalize(process.argv[3]));
} catch(e) {}

var executabledir = __dirname;

child_process.execFileSync('java', ['-cp', path.join(executabledir, '..', 'classes'), 'com.telerik.metadata.Generator', process.argv[2]], {stdio: 'inherit'});

var inputFiles = fs.readdirSync(process.argv[2]);
console.log("Listing input files");
console.log(fs.realpathSync(process.argv[2]));
console.log(inputFiles.join('\n'));
console.log("Listing input files. end");

outputFiles = fs.readdirSync('bin');
console.log("Listing output files");
console.log(fs.realpathSync(process.argv[3]));
console.log(outputFiles.join('\n'));
console.log("Listing output files. end");

outputFiles.forEach(function(filename) {
    fs.renameSync(path.join('bin', filename), path.join(process.argv[3], filename));
});

fs.rmdirSync('bin');
