package com.couchbase.perf.sdk.stages

import com.couchbase.context.StageContext
import com.couchbase.stages.Stage
import groovy.transform.CompileStatic

@CompileStatic
class Log extends Stage {
    private final String toLog

    Log(String toLog) {
        this.toLog = toLog
    }

    @Override
    String name() {
        return "Log: ${toLog}"
    }

    @Override
    protected void executeImpl(StageContext ctx) {
    }
}