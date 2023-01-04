package com.couchbase.perf.sdk.stages

import com.couchbase.context.StageContext
import com.couchbase.stages.Stage
import com.couchbase.tools.performer.BuildDockerNodePerformer


class BuildDockerNodeSDKPerformer extends Stage {
    private final String imageName
    private final String sdkVersion
    private final String sha

    static String genImageName(String sdkVersion) {
        return "performer-node" + sdkVersion
    }

    BuildDockerNodeSDKPerformer(String imageName, String sdkVersion, String sha) {
        this.sdkVersion = sdkVersion
        this.imageName = imageName
        this.sha = sha
    }

    BuildDockerNodeSDKPerformer(String sdkVersion, String sha) {
        this(BuildDockerNodeSDKPerformer.genImageName(sdkVersion), sdkVersion, sha)
    }

    @Override
    String name() {
        return "Building image ${imageName}"
    }

    @Override
    protected void executeImpl(StageContext ctx) {
        ctx.env.log("Building node snapshot image")
        BuildDockerNodePerformer.build(ctx.env, ctx.sourceDir(), Optional.of(sdkVersion), imageName, Optional.ofNullable(sha))
    }

    String getImageName() {
        return this.imageName
    }
}
