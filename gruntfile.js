//Builds the metadata generator tool and
//   uses it to generate the Android metadata.

var pathModule = require("path");

module.exports = function(grunt) {
    var outDir = "./dist";
    var srcDir = ".";

    var args = {
        jarsSrc: grunt.option("jarsPath") || "./jars"
    };

    grunt.initConfig({
        pkg: grunt.file.readJSON(srcDir + "/package.json"),
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
            }
        },
        exec: {
            antBuild: {
                cmd: "ant build",
                cwd: "./src/"
            },
            runTool: {
                cmd: "java -cp ./jars/*:. com.telerik.bindings.Generator",
                cwd: "./src/"
            },
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
                            "clean:runnableTool",
                            //TODO: COPY THE OUTPUT SOMEWHERE!
                        ]);


    grunt.registerTask("default", [
                            "buildGenerator",
                            "generateMetadata",
//                           The following two calls should not exist.
//                              They are a hack until the binding generator starts working properly:
//                             "clean:HACKgeneratedAndroid17Bindings",
//                             "unzip:HACKCompilableGeneratedBindings"
                        ]);

}
