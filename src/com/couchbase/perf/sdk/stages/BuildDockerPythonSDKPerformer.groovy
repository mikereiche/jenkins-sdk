package com.couchbase.perf.sdk.stages

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
        // Build context needs to be perf-sdk as we need the .proto files
        ctx.inSourceDirAbsolute {
            imp.dir('transactions-fit-performer') {
                imp.dir('performers/python') {
                    writeRequirementsFile(imp)
                }
                imp.execute("docker build -f performers/python/Dockerfile -t $imageName .")
            }
        }
    }

    private List writeRequirementsFile(Environment imp) {
        def requirements = new File("${imp.currentDir()}/requirements.txt")
        def lines = requirements.readLines()
        requirements.write("")

        for (int i = 0; i < lines.size(); i++) {
            def line = lines[i]

            if (line.contains("git+https://github.com/couchbase/couchbase-python-client.git")) {
                requirements.append("git+https://github.com/couchbase/couchbase-python-client.git@${sdkVersion}\n")
            } else {
                requirements.append(line + "\n")
            }
        }
    }

    String getImageName(){
        return imageName
    }
}
