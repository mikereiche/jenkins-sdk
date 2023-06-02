package com.couchbase.perf.sdk.stages

import com.couchbase.context.StageContext
import com.couchbase.stages.Stage
import com.couchbase.tools.performer.BuildDockerDotNetPerformer
import com.couchbase.tools.performer.VersionToBuildUtil
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
        BuildDockerDotNetPerformer.build(ctx.env, ctx.sourceDir(), VersionToBuildUtil.from(sdkVersion, sha), imageName)
    }

    String getImageName() {
        return imageName
    }
}