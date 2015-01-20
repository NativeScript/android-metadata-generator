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
            runnableTool: {
                src: ["./src/com/"]
            },
            generatedMetadata: {
                src: ["./src/bin/out/"]
            },
            jars: {
                src: ["./src/jars"]
            },
            outputDir: {
                src: "./dist"
            },
            resultDir: {
                src: ["./dist/**/*.*", "./dist/**/*", "!./dist/*.tgz"]
            }
        },
        exec: {
            antBuild: {
                cmd: "ant build",
                cwd: "./src/"
            },
            runTool: {
                cmd: "java -cp ./jars/*:. com.telerik.metadata.Generator",
                cwd: "./src/"
            },
            npmPack: {
                cmd: "npm pack",
                cwd: "./dist"
            }
        },
        copy: {
            jars: {
                expand: true,
                force: true,
                src: pathModule.join(args.jarsSrc, "**/*.jar"),
                dest: "./src/jars/"
            },
            toolToRoot: {
                expand: true,
                cwd: "./src/bin",
                src: [
                    "./com/**/*.*"
                ],
                dest: "./src/"
            },
            collectResultFiles: {
                src: ["./src/bin/*.dat", "./package.json"],
                dest: "./dist/"
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

    grunt.registerTask("generateMetadata", [
                            "clean:runnableTool",
                            "copy:toolToRoot",
                            "clean:generatedMetadata",
                            "clean:jars",
                            "copy:jars",
                            "exec:runTool",
                            "clean:runnableTool"
                        ]);

    grunt.registerTask("packResult", [
                "clean:outputDir",
                "copy:collectResultFiles",
                "exec:npmPack",
                "clean:resultDir",
            ]);

    grunt.registerTask("default", [
                            "buildGenerator",
                            "generateMetadata",
                            "packResult"
                        ]);

}
