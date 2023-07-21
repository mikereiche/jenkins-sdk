package com.couchbase.perf.sdk.stages


import com.couchbase.stages.servers.InitialiseCluster
import com.couchbase.stages.Stage
import groovy.json.JsonBuilder
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import com.couchbase.context.StageContext

/**
 * Runs the driver and waits for the result.
 */
@CompileStatic
class RunSDKDriver extends Stage {
    private final OutputPerformerConfig stageOutput

    RunSDKDriver(OutputPerformerConfig stageOutput) {
        this.stageOutput = stageOutput
    }

    @Override
    String name() {
        return "Run for ${stageOutput.outputFilenameAbs()}"
    }

    @CompileDynamic
    @Override
    void executeImpl(StageContext ctx) {
        // Make Docker happy on Windows
        def source = stageOutput.outputFilenameAbs()//.replace('\\', '/')
        ctx.env.execute("${ctx.env.isWindows() ? "" : "timeout 24h "}docker run --rm --network perf -v ${source}:/app/run-config.yaml driver /app/run-config.yaml",
            false, false, true)
    }
}