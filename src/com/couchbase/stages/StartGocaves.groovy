package com.couchbase.stages

import com.couchbase.context.StageContext
import groovy.transform.CompileStatic

/**
 * Starts a Gocaves cluster in mock-only mode
 */
@CompileStatic
class StartGocaves extends Stage {
    String source
    int port
    String hostname

    StartGocaves(String source, int port, String hostname){
        this.source = source
        this.port = port
        this.hostname = hostname
    }

    @Override
    String name() {
        return "Start Gocaves"
    }

    @Override
    void executeImpl(StageContext ctx) {
        def imp = ctx.env

        imp.dirAbsolute(source) {
            imp.execute("go mod download")
            imp.execute("go run tools/prebuildstep.go")
            // run gocaves in the background as we don't want the process to end
            imp.execute("go run main.go -mock-only -force-port $port &")
        }
    }

    @Override
    void finishImpl(StageContext ctx) {}

    String clusterIp() {
        return "$hostname:$port"
    }
}
