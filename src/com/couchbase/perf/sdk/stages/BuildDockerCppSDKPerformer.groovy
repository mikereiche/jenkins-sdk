package com.couchbase.perf.sdk.stages

import com.couchbase.context.StageContext
import com.couchbase.stages.Stage
import com.couchbase.tools.performer.BuildDockerCppPerformer

class BuildDockerCppSDKPerformer extends Stage {

    private final String sdkVersion
    private final String sha
    final String imageName

    static String genImageName(String sdkVersion) {
        return "performer-cpp" + sdkVersion
    }

    BuildDockerCppSDKPerformer(String sdkVersion, String sha) {
        this(genImageName(sdkVersion), sdkVersion, sha)
    }

    BuildDockerCppSDKPerformer(String imageName, String sdkVersion, String sha) {
        this.sdkVersion = sdkVersion
        this.imageName = imageName
        this.sha = sha
    }

    @Override
    String name() {
        return "Building image ${imageName}"
    }

    @Override
    void executeImpl(StageContext ctx) {
        BuildDockerCppPerformer.build(ctx.env, ctx.sourceDir(), Optional.of(sdkVersion), Optional.ofNullable(sha), imageName)
    }

    String getImageName(){
        return imageName
    }
}
