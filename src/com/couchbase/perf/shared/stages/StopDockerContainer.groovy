package com.couchbase.perf.shared.stages

import com.couchbase.context.StageContext
import com.couchbase.stages.Stage
import groovy.transform.CompileStatic

@CompileStatic
class StopDockerContainer extends Stage {

    private String containerName

    StopDockerContainer(String containerName) {
        this.containerName = containerName
    }

    @Override
    String name() {
        return "Stopping docker container ${containerName}"
    }

    @Override
    void executeImpl(StageContext ctx) {
        try {
            ctx.env.execute("docker kill ${containerName}", false, false)
        }
        catch(RuntimeException err) {
        }

        try {
            ctx.env.execute("docker rm ${containerName}", false, false)
        }
        catch(RuntimeException err) {
        }
    }

    @Override
    void finishImpl(StageContext ctx) {
    }
}