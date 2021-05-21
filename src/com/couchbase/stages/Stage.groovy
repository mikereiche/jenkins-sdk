package com.couchbase.stages

import groovy.transform.CompileStatic
import com.couchbase.context.StageContext


// Abstracts a particular task/stage - spinning up a cluster, starting a process.  It will map to a Jenkins stage
// in that environment.
@CompileStatic
abstract class Stage {
    abstract String name()

    default void execute(StageContext ctx) {
        ctx.env.startStage(this)
        stagesPre(ctx).forEach(stage -> stage.execute(ctx))
        if (ctx.allowedToExecute(this)) {
            executeImpl(ctx)
        }
    }

    /**
     * A stage is free to return a list of other Stages to execute before itself.
     * These will not be mapped to individual Jenkins stages.
     */
    protected List<Stage> stagesPre(StageContext ctx) {
        return []
    }

    protected abstract void executeImpl(StageContext ctx)

    protected void finishImpl(StageContext ctx) {}

    void finish(StageContext ctx) {
        def stages = stagesPre(ctx)
        if (!ctx.dryRun) {
            finishImpl(ctx)
        }
        stages.forEach(stage -> stage.finish(ctx))
        ctx.env.stopStage(this)
    }
}