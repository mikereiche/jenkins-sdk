package com.couchbase.perf.shared.stages

import com.couchbase.context.StageContext
import com.couchbase.stages.Stage
import groovy.transform.CompileStatic

@CompileStatic
class PruneDocker extends Stage {

    @Override
    String name() {
        return "Pruning Docker"
    }

    @Override
    void executeImpl(StageContext ctx) {
        try {
            ctx.env.execute("docker builder prune -a", false, false)
        }
        catch(RuntimeException err) {
        }
    }

    @Override
    void finishImpl(StageContext ctx) {
    }
}