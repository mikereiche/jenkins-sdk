package com.couchbase.perf.sdk.stages

import com.couchbase.context.StageContext
import com.couchbase.stages.Stage
import groovy.transform.CompileStatic

@CompileStatic
class BuildDockerDotNetSDKPerformer extends Stage {

    private final String sdkVersion
    private final String sha
    private final String imageName

    // Will have one of these two:
    // sdkVersion = "3.3.3"        and sha = null
    // sdkVersion = "3.3.3-6aefd5" and sha = "6aefd5"
    BuildDockerDotNetSDKPerformer(String sdkVersion, String sha) {
        this.sdkVersion = sdkVersion
        this.sha = sha
        this.imageName = "performer-dotnet-" + sdkVersion
    }

    @Override
    String name() {
        return "Building .NET image ${imageName}"
    }

    @Override
    void executeImpl(StageContext ctx) {
        ctx.inSourceDirAbsolute {
            ctx.env.rmdir(ctx.env.currentDir() + "/couchbase-net-client")

            if (sha == null) {
                ctx.env.execute("git clone --branch ${sdkVersion} https://www.github.com/couchbase/couchbase-net-client.git", false, true, false)
            }
            else {
                ctx.env.checkout("https://www.github.com/couchbase/couchbase-net-client")
                ctx.env.dir("couchbase-net-client", {
                    ctx.env.execute("git checkout ${sha}")
                })
            }
            ctx.env.execute("docker build -f transactions-fit-performer/performers/dotnet/Couchbase.Transactions.FitPerformer/Dockerfile -t $imageName .")
        }
    }

    String getImageName(){
        return imageName
    }
}