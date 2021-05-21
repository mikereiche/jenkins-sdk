package com.couchbase.stages

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import com.couchbase.context.StageContext
import com.couchbase.fit.perf.config.PerfConfig
import com.couchbase.fit.stages.BuildDockerJavaFITPerformer
import com.couchbase.fit.stages.StartDockerImagePerformer

/**
 * Builds, copies (if needed), and runs a performer
 */
@CompileStatic
class InitialisePerformer extends Stage {
    private PerfConfig.Implementation impl
    private int port = 8060

    InitialisePerformer(PerfConfig.Implementation impl) {
        this.impl = impl
        if (impl.port != null) {
            port = impl.port
        }
    }

    @Override
    String name() {
        return "Init performer $impl"
    }

    @CompileDynamic
    boolean skipDockerBuild(StageContext ctx) {
        return ctx.jc.settings.skipDockerBuild
    }

    @Override
    List<Stage> stagesPre(StageContext ctx) {
        if (impl.port != null) {
            // Nothing to do
            return []
        }
        else {
            if (impl.language == "java") {
                List<Stage> stages = []
                def stage1 = new BuildDockerJavaFITPerformer(impl.version)

                if (!skipDockerBuild(ctx)) {
                    stages.add(stage1)
                }

                if (ctx.performerServer == "localhost") {
                    stages.add(new StartDockerImagePerformer(stage1.imageName, port, impl.version))
                } else {
                    throw new IllegalArgumentException("Cannot handle running on performer remote server")
                }

                return stages
            }

            throw new IllegalArgumentException("Unknown performer ${impl.language}")
        }
    }

    @Override
    void executeImpl(StageContext ctx) {}

    String hostname() {
        // All we support currently
        return "localhost"
    }

    int port() {
        return port
    }

    boolean isDocker() {
        return impl.port == null
    }
}