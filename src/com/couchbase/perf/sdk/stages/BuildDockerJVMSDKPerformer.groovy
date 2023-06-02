package com.couchbase.perf.sdk.stages

import com.couchbase.context.StageContext
import com.couchbase.stages.Stage
import com.couchbase.tools.performer.BuildDockerJVMPerformer
import com.couchbase.tools.performer.VersionToBuildUtil
import groovy.transform.CompileStatic

@CompileStatic
class BuildDockerJVMSDKPerformer extends Stage {

    private final String client // "java"
    // "3.3.3" or "3.3.3-6abad3" or "refs/changes/94/184294/1"
    private final String sdkVersion
    final String imageName

    static String genImageName(String sdkVersion, String jvm) {
        return "performer-" + jvm + sdkVersion
    }

    BuildDockerJVMSDKPerformer(String client, String sdkVersion) {
        this.client = client
        this.sdkVersion = sdkVersion
        this.imageName = genImageName(sdkVersion, client)
    }

    @Override
    String name() {
        return "Building JVM image ${imageName}"
    }

    @Override
    void executeImpl(StageContext ctx) {
        try {
            BuildDockerJVMPerformer.build(ctx.env, ctx.sourceDir(), client, VersionToBuildUtil.from(sdkVersion, null), imageName)
        }
        catch (err) {
            ctx.env.log(err.toString())
            err.printStackTrace()
            throw err
        }
    }

    String getImageName(){
        return imageName
    }
}