package com.couchbase.perf.sdk.stages


import com.couchbase.stages.servers.InitialiseCluster
import com.couchbase.stages.Stage
import groovy.json.JsonBuilder
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import com.couchbase.context.StageContext

/**
 * Outputs the runner config
 */
@CompileStatic
class RunRunner extends Stage {
    private final InitialiseCluster stageCluster
    private final InitialiseSDKPerformer stagePerf
    private final OutputPerformerConfig stageOutput

    RunRunner(InitialiseCluster cluster, InitialiseSDKPerformer stagePerf, OutputPerformerConfig stageOutput) {
        this.stageCluster = cluster
        this.stagePerf = stagePerf
        this.stageOutput = stageOutput
    }

    @Override
    String name() {
        return "Run for ${stageOutput.outputFilenameAbs()}"
    }

    @CompileDynamic
    @Override
    void executeImpl(StageContext ctx) {
        ctx.inSourceDir {
            ctx.env.executeSimple("docker build -f sdk-driver/Dockerfile -t driver .")
            ctx.env.log(ctx.env.executeSimple("docker run --rm --network perf driver /app/" + stageOutput.outputFilenameAbs()))
        }
    }
}