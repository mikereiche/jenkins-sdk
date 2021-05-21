package com.couchbase.fit.perf.main

import com.couchbase.context.StageContext
import com.couchbase.context.environments.EnvironmentLocal
import com.couchbase.fit.perf.config.ConfigParser
import com.couchbase.fit.perf.config.PerfConfig
import com.couchbase.fit.perf.config.Run
import com.couchbase.fit.perf.database.PerfDatabase
import com.couchbase.fit.perf.database.RunFromDb
import com.couchbase.stages.*
import groovy.transform.CompileStatic
import groovy.yaml.YamlSlurper

import java.util.stream.Collectors

import static java.util.stream.Collectors.groupingBy

@CompileStatic
static List<Run> parseConfig(StageContext ctx) {
    def config = ConfigParser.readPerfConfig("job-config.yaml")
    def allPerms = ConfigParser.allPerms(config)
    return allPerms
}

//@CompileStatic
static Map<PerfConfig.Cluster, List<Run>> parseConfig2(StageContext ctx, List<RunFromDb> fromDb) {
    /**
     * Config file declaratively says what runs should exist.  Our job is to comapre to runs that do exist, and run any required.
     *
     * Read all permutations
     * See what runs already exist
     * Group these by cluster, then by performer. Each cluster-performer pair is going to run '2nd chunk'
     * For each cluster, bring it up
     * For each cluster-performer in that cluster
     * - Build and bring up the performer
     * - Run it with required runs. Ah hmm will need to fully unroll the variables here.
     * - Bring down performer
     * Bring down cluster
     */

//    def allPerms = parseConfig(ctx)

    def toRun = fromDb.stream()
            .filter(run -> run.dbRunIds.isEmpty() || ctx.force)
            .map(run -> run.run)
            .collect(Collectors.toList())

    def groupedByCluster = toRun.stream()
            .collect(groupingBy((Run run) -> run.cluster))

    ctx.env.log("Have ${toRun.size()} runs not in database, requiring ${groupedByCluster.size()} clusters")

    return groupedByCluster
}

List<Stage> plan(StageContext ctx, Map<PerfConfig.Cluster, List<Run>> input) {
    def stages = new ArrayList<Stage>()

    input.forEach((cluster, runsForCluster) -> {
        def clusterStage = new InitialiseCluster(cluster)
        def clusterChildren = new ArrayList<Stage>()

        def groupedByPerformer = runsForCluster.stream()
                .collect(groupingBy((Run run) -> run.impl))

        ctx.env.log("Cluster ${cluster} requires ${groupedByPerformer.size()} performers")

        groupedByPerformer.forEach((performer, runsForClusterAndPerformer) -> {
            def performerStage = new InitialisePerformer(performer)
            def runId = UUID.randomUUID().toString()
            def configFilename = runId + ".yaml"
            def performerRuns = []

            def output = new OutputPerformerConfig(
                                clusterStage,
                                performerStage,
                                jc,
                                cluster,
                                performer,
                                runsForClusterAndPerformer,
                                configFilename)
            performerRuns.add(output)
            performerRuns.add(new RunRunner(clusterStage, performerStage, output))

            clusterChildren.add(new ScopedStage(performerStage, performerRuns))
        })

        stages.add(new ScopedStage(clusterStage, clusterChildren))
    })

    return stages
}


void execute() {
    def ys = new YamlSlurper()
    def jc = ys.parse(new File("config/job-config.yaml"))
    def env = new EnvironmentLocal(jc.environment)

    def ctx = new StageContext()
    ctx.jc = jc
    ctx.env = env
    ctx.performerServer = jc.servers.performer
    ctx.dryRun = jc.settings.dryRun
    ctx.force = jc.settings.force
    def allPerms = parseConfig(ctx)
    def db = PerfDatabase.compareRunsAgainstDb(ctx, allPerms)
    def parsed2 = parseConfig2(ctx, db)
    def planned = plan(ctx, parsed2)
    def root = new Stage() {
        @Override
        String name() {
            return "Root"
        }

        protected List<Stage> stagesPre(StageContext _) {
            return planned
        }

        @Override
        protected void executeImpl(StageContext _) {}
    }
    root.execute(ctx)
    root.finish(ctx)
//run(ctx, planned)
//print(planned)
}