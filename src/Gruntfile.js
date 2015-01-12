//Builds the bindings generator tool and
//   uses it to generate the Android bindings and the metadata.

var generatedBindingsDir = "../generated";
var generatedAPI17 = generatedBindingsDir + "/api17.zip";

module.exports = function(grunt) {
    var outDir = "./dist";
    var srcDir = ".";

    grunt.initConfig({
        pkg: grunt.file.readJSON(srcDir + "/package.json"),
        clean: {
            tool: {
                src: ["./bin/com/"]
            },
            runnableTool: {
                src: ["./com/"]
            },
            generatedBindings: {
                src: ["./bin/out/"]
            },
            HACKgeneratedAndroid17Bindings: {
                src: ["./bin/out/EXTRACTED"]
            }
        },
        exec: {
            antBuild: {
                cmd: "ant build"
            },
            runTool: {
                cmd: "java -cp ./jars/*:. com.telerik.bindings.Generator"
            },
        },
        copy: {
            toolToRoot: {
                expand: true,
                cwd: "./bin/",
                src: [
                    "./com/**/*.*"
                ],
                dest: "./"
            }
        },
        unzip: {
            HACKCompilableGeneratedBindings: {
                expand: true,
                src: generatedAPI17,
                dest: "./bin/out/EXTRACTED/"
            }
        }
    });

    grunt.loadNpmTasks("grunt-contrib-clean");
    grunt.loadNpmTasks("grunt-exec");
    grunt.loadNpmTasks("grunt-contrib-copy");
    grunt.loadNpmTasks("grunt-zip");
    
    grunt.registerTask("buildGenerator", [
                            "clean:tool",
                            "exec:antBuild"
                        ]);

    grunt.registerTask("generateBindings", [
                            "clean:runnableTool",
                            "copy:toolToRoot",
                            "clean:generatedBindings",
                            "exec:runTool",
                            "clean:runnableTool"
                        ]);


    grunt.registerTask("default", [
                            "buildGenerator",
                            "generateBindings",
//                           The following two calls should not exist.
//                              They are a hack until the binding generator starts working properly:
                             "clean:HACKgeneratedAndroid17Bindings",
                             "unzip:HACKCompilableGeneratedBindings"
                        ]);

}
