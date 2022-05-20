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
            //TODO change "performer" to an image name or something, the driver isnt playing well with having the image name as a name atm
            ctx.env.execute("172.23.104.43:2376 docker kill performer")
        }
        catch(RuntimeException err) {
        }

        //TODO change "performer" to an image name or something, the driver isnt playing well with having the image name as a name atm
        ctx.env.execute("172.23.104.43:2376 docker run --rm --network perf -d -p $port:8060 --name performer $imageName version=$version")
    }

    @Override
    void finishImpl(StageContext ctx) {
        //TODO change "performer" to an image name or something, the driver isnt playing well with having the image name as a name atm
        ctx.env.execute("172.23.104.43:2376 docker kill performer")
        // uncomment this when uploading to CI
        //ctx.env.execute("docker system prune")
    }
}