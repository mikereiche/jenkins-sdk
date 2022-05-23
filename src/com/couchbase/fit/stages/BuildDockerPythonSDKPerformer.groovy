package com.couchbase.fit.stages

import com.couchbase.context.StageContext
import com.couchbase.context.environments.Environment
import com.couchbase.stages.Stage

class BuildDockerPythonSDKPerformer extends Stage{

    private final String sdkVersion
    final String imageName

    static String genImageName(String sdkVersion) {
        return "performer-python" + sdkVersion
    }

    BuildDockerPythonSDKPerformer(String sdkVersion) {
        this(BuildDockerPythonSDKPerformer.genImageName(sdkVersion), sdkVersion)
    }

    BuildDockerPythonSDKPerformer(String imageName, String sdkVersion) {
        this.sdkVersion = sdkVersion
        this.imageName = imageName
    }

    @Override
    String name() {
        return "Building image ${imageName}"
    }

    @Override
    void executeImpl(StageContext ctx) {
        def imp = ctx.env
//        imp.tempDir() {
            // Build context needs to be perf-sdk as we need the .proto files
            ctx.inSourceDir {
//                imp.dir('performers/python') {
//                    writeRequirementsFile(imp)
//                }
                imp.execute("DOCKER_HOST=172.23.104.43:2376 docker build -f performers/python/Dockerfile -t $imageName .")
            }
//        }
    }

//    private List writeRequirementsFile(Environment imp) {
//        def requirements = new File("${imp.currentDir()}/requirements.txt")
//        def lines = requirements.readLines()
//        requirements.write("")
//
//        for (int i = 0; i < lines.size(); i++) {
//            def line = lines[i]
//
//            if (line.contains("couchbase")) {
//                requirements.append("couchbase==${sdkVersion}\n")
//            } else {
//                requirements.append(line + "\n")
//            }
//        }
//    }

    String getImageName(){
        return imageName
    }
}
