package com.couchbase.perf.sdk.stages

import com.couchbase.context.StageContext
import com.couchbase.stages.Stage
import groovy.transform.CompileStatic

@CompileStatic
class Defer extends Stage {
    private final Runnable deferred

    Defer(Runnable deferred) {
        this.deferred = deferred
    }

    @Override
    String name() {
        return "Deferred"
    }

    @Override
    protected void executeImpl(StageContext ctx) {
        deferred.run()
    }
}