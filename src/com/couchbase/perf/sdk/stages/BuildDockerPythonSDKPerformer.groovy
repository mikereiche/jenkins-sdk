package com.couchbase.perf.sdk.stages

import com.couchbase.context.StageContext
import com.couchbase.stages.Stage
import com.couchbase.tools.performer.BuildDockerPythonPerformer
import com.couchbase.tools.performer.VersionToBuildUtil

class BuildDockerPythonSDKPerformer extends Stage {

    private final String sdkVersion
    private final String sha
    final String imageName

    static String genImageName(String sdkVersion) {
        return "performer-python" + sdkVersion
    }

    BuildDockerPythonSDKPerformer(String sdkVersion, String sha) {
        this(genImageName(sdkVersion), sdkVersion, sha)
    }

    BuildDockerPythonSDKPerformer(String imageName, String sdkVersion, String sha) {
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
        BuildDockerPythonPerformer.build(ctx.env, ctx.sourceDir(), VersionToBuildUtil.from(sdkVersion, sha), imageName)
    }

    String getImageName(){
        return imageName
    }
}
