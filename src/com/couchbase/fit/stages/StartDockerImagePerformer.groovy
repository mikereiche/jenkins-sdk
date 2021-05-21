package com.couchbase.fit.stages

import groovy.transform.CompileStatic
import com.couchbase.context.StageContext
import com.couchbase.stages.Stage

@CompileStatic
class StartDockerImagePerformer extends Stage {

    private String imageName
    private int port
    private final String version

    StartDockerImagePerformer(String imageName, int port, String version) {
        this.version = version
        this.imageName = imageName
        this.port = port
    }

    @Override
    String name() {
        return "Start docker image $imageName on $port"
    }

    @Override
    void executeImpl(StageContext ctx) {
        try {
            ctx.env.execute("docker kill $imageName")
        }
        catch(RuntimeException err) {
        }

        ctx.env.execute("docker run --rm -d -p $port:8060 --name $imageName $imageName version=$version")
    }

    @Override
    void finishImpl(StageContext ctx) {
        ctx.env.execute("docker kill $imageName")
    }
}