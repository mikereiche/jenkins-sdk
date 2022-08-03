package com.couchbase.perf.sdk.stages

import com.couchbase.context.StageContext
import com.couchbase.stages.Stage
import com.couchbase.tools.performer.BuildDockerJVMPerformer
import groovy.transform.CompileStatic

@CompileStatic
class BuildDockerJVMSDKPerformer extends Stage {

    private final String client // "java"
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
        BuildDockerJVMPerformer.build(ctx.env, ctx.sourceDir(), client, sdkVersion, imageName)
    }

    String getImageName(){
        return imageName
    }
}