package com.couchbase.stages.servers

import com.couchbase.context.StageContext
import com.couchbase.stages.Stage
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
            imp.execute("docker build -t gocaves .")
            imp.execute("docker run -d -p 8080:8080 --rm --network perf --name $hostname gocaves -mock-only -force-port $port")
        }
    }

    @Override
    void finishImpl(StageContext ctx) {
        ctx.env.execute("docker kill $hostname")
    }

    String clusterIp() {
        return "$hostname:$port"
    }
}
