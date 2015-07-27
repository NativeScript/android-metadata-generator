//Builds the metadata generator tool and
//   uses it to generate the Android metadata.

var pathModule = require("path");

module.exports = function(grunt) {

    var args = {
        jarsSrc: grunt.option("jarsPath") || "./jars"
    };

    grunt.initConfig({
        pkg: grunt.file.readJSON("./package.json"),
        clean: {
            tool: {
                src: ["./src/bin/com/"]
            },
            outputDir: {
                src: "./dist"
            }
        },
        exec: {
            antBuild: {
                cmd: "ant build",
                cwd: "./src/"
            },
            npmPack: {
                cmd: "npm pack",
                cwd: "./dist"
            }
        },
        copy: {
            runnableTool: {
                expand: true,
                cwd: "./src/bin",
                src: [
                    "./com/**/*.*"
                ],
                dest: "./dist/classes/"
            },
            packageFiles: {
                files: [
                    {src: "./package.json", dest: "./dist/"},
                    {expand: true, src: "./generate-metadata.sh", cwd: "./build/", dest: "./dist/bin/"},
                    {expand: true, src: "./generate-metadata.js", cwd: "./build/", dest: "./dist/bin/"}
                ]
            }
        }
    });

    grunt.loadNpmTasks("grunt-contrib-clean");
    grunt.loadNpmTasks("grunt-exec");
    grunt.loadNpmTasks("grunt-contrib-copy");
    
    grunt.registerTask("buildGenerator", [
                            "clean:tool",
                            "exec:antBuild"
                        ]);

    grunt.registerTask("packGenerator", [
                "clean:outputDir",
                "copy:runnableTool",
                "copy:packageFiles",
                "exec:npmPack"
            ]);

    grunt.registerTask("default", [
                            "buildGenerator",
                            "packGenerator"
                        ]);

}
