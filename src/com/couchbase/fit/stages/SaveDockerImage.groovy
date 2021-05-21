package com.couchbase.fit.stages

import groovy.transform.CompileStatic
import com.couchbase.context.StageContext
import com.couchbase.stages.Stage

@CompileStatic
class SaveDockerImage extends Stage {

    private String imageName

    SaveDockerImage(String imageName) {
        this.imageName = imageName
    }

    @Override
    String name() {
        return "Save docker image $imageName"
    }

    @Override
    void executeImpl(StageContext ctx) {
        ctx.env.tempDir() {
            def outputName = "${imageName}.tar"
            ctx.env.execute("docker save -o $outputName $imageName")
        }
    }
}