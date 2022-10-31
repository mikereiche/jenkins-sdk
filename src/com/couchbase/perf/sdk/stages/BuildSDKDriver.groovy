package com.couchbase.perf.sdk.stages

import com.couchbase.context.StageContext
import com.couchbase.stages.Stage
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

/**
 * Builds the SDK driver
 */
@CompileStatic
class BuildSDKDriver extends Stage {
    BuildSDKDriver() {
    }

    @Override
    String name() {
        return "Build SDK driver"
    }

    @CompileDynamic
    @Override
    void executeImpl(StageContext ctx) {
        ctx.inSourceDirAbsolute {
            ctx.env.dir('transactions-fit-performer') {
                ctx.env.execute("docker build -f perf-driver/Dockerfile -t driver .", false, true, true)
            }
        }
    }
}