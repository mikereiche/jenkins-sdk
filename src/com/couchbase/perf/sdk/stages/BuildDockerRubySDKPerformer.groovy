package com.couchbase.perf.sdk.stages

import com.couchbase.context.StageContext
import com.couchbase.stages.Stage
import com.couchbase.tools.performer.BuildDockerRubyPerformer
import com.couchbase.tools.performer.VersionToBuildUtil

class BuildDockerRubySDKPerformer extends Stage {

    private final String sdkVersion
    private final String sha
    final String imageName

    static String genImageName(String sdkVersion) {
        return "performer-ruby" + sdkVersion
    }

    BuildDockerRubySDKPerformer(String sdkVersion, String sha) {
        this(genImageName(sdkVersion), sdkVersion, sha)
    }

    BuildDockerRubySDKPerformer(String imageName, String sdkVersion, String sha) {
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
        BuildDockerRubyPerformer.build(ctx.env, ctx.sourceDir(), VersionToBuildUtil.from(sdkVersion, sha), imageName)
    }

    String getImageName() {
        return imageName
    }
}
