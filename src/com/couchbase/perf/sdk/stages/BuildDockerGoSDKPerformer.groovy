package com.couchbase.perf.sdk.stages

import com.couchbase.context.StageContext
import com.couchbase.context.environments.Environment
import com.couchbase.stages.Stage

class BuildDockerGoSDKPerformer extends Stage{

    private final String sdkVersion
    final String imageName

    static String genImageName(String sdkVersion) {
        return "performer-go" + sdkVersion
    }

    BuildDockerGoSDKPerformer(String sdkVersion) {
        this(BuildDockerGoSDKPerformer.genImageName(sdkVersion), sdkVersion)
    }

    BuildDockerGoSDKPerformer(String imageName, String sdkVersion) {
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
                imp.dir('performers/go') {
                    writeGoModFile(imp)
                }
                imp.execute("docker build -f performers/go/Dockerfile -t $imageName .")
            }
        }
    }

    private List writeGoModFile(Environment imp) {
        def goMod = new File("${imp.currentDir()}/go.mod")
        def lines = goMod.readLines()

        goMod.write("")

        for (int i = 0; i < lines.size(); i++) {
            def line = lines[i]

            if (line.contains("couchbase/gocb/v2")) {
                goMod.append("\tgithub.com/couchbase/gocb/v2 v${sdkVersion}\n")
            } else {
                goMod.append(line + "\n")
            }
        }

    }

    String getImageName(){
        return imageName
    }
}
