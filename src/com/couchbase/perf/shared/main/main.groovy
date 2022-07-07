package com.couchbase.perf.shared.main

import com.couchbase.context.StageContext
import com.couchbase.context.environments.Environment
import com.couchbase.perf.sdk.stages.BuildSDKDriver
import com.couchbase.perf.sdk.stages.InitialiseSDKPerformer
import com.couchbase.perf.sdk.stages.OutputPerformerConfig
import com.couchbase.perf.sdk.stages.RunSDKDriver
import com.couchbase.perf.shared.config.ConfigParser
import com.couchbase.perf.shared.config.PerfConfig
import com.couchbase.perf.shared.config.Run
import com.couchbase.perf.shared.database.PerfDatabase
import com.couchbase.perf.shared.database.RunFromDb
import com.couchbase.perf.shared.stages.StopDockerContainer
import com.couchbase.stages.*
import com.couchbase.stages.servers.InitialiseCluster
import groovy.transform.CompileStatic
import groovy.yaml.YamlSlurper

import java.util.stream.Collectors

import static java.util.stream.Collectors.groupingBy


class Execute {
    static void jcPrep(StageContext ctx, String[] args){
        //Get timescaledb password from jenkins credential
        String dbPwd = ""
        if (args.length > 0) {
            dbPwd = args[0]
            ctx.jc.database.password = args[0]
        }

        // todo needs to move
        // String mostRecentCommit = ctx.env.executeSimple("git ls-remote https://github.com/couchbase/couchbase-python-client.git HEAD | tail -1 | sed 's/HEAD//g'")

    }

    //The password gets written to job config and as Jenkins keeps the jobconfig file it needs to get removed
    static void jcCleanup(){
        def jobConfig = new File("config/job-config.yaml")
        def lines = jobConfig.readLines()
        def changePwd = false

        jobConfig.write("")

        for (int i = 0; i < lines.size(); i++) {
            def line = lines[i]

            if (line.contains("database:")){
                changePwd = true
                jobConfig.append(line + "\n")
            } else if (changePwd && line.contains("password")){
                jobConfig.append("  password: password\n")
                changePwd = false
            } else {
                jobConfig.append(line + "\n")
            }
        }
    }

    @CompileStatic
    static List<Run> parseConfig(StageContext ctx) {
        def config = ConfigParser.readPerfConfig("config/job-config.yaml")
        def allPerms = ConfigParser.allPerms(ctx, config)
        return allPerms
    }

    //@CompileStatic
    static Map<PerfConfig.Cluster, List<Run>> parseConfig2(StageContext ctx, List<RunFromDb> fromDb) {
        /**
         * Config file declaratively says what runs should exist.  Our job is to compare to runs that do exist, and run any required.
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

        ctx.env.log("Have ${toRun.size()} runs not in database (or forced rerun), requiring ${groupedByCluster.size()} clusters")
        return groupedByCluster
    }

    static List<Stage> plan(StageContext ctx, Map<PerfConfig.Cluster, List<Run>> input, jc) {
        def stages = new ArrayList<Stage>()

        input.forEach((cluster, runsForCluster) -> {
            def clusterStage = new InitialiseCluster(cluster)
            def clusterChildren = new ArrayList<Stage>()

            def groupedByPerformer = runsForCluster.stream()
                    .collect(groupingBy((Run run) -> run.impl))

            ctx.env.log("Cluster ${cluster} requires ${groupedByPerformer.size()} performers")

            groupedByPerformer.forEach((performer, runsForClusterAndPerformer) -> {
                def performerRuns = []

                def performerStage = new InitialiseSDKPerformer(performer)
                def runId = UUID.randomUUID().toString()
                def configFilenameAbs = "${ctx.env.workspaceAbs}${File.separatorChar}${runId}.yaml"

                def output = new OutputPerformerConfig(
                        clusterStage,
                        performerStage,
                        jc,
                        cluster,
                        performer,
                        runsForClusterAndPerformer,
                        configFilenameAbs)

                performerRuns.add(new StopDockerContainer(InitialiseSDKPerformer.CONTAINER_NAME))
                performerRuns.add(output)
                if (!ctx.skipDockerBuild()) {
                    performerRuns.add(new BuildSDKDriver())
                }

                clusterChildren.addAll(performerRuns)
                // ScopedStage because we want to bring performer up, run driver, bring performer down
                clusterChildren.add(new ScopedStage(performerStage, [new RunSDKDriver(output)]))
            })

            stages.add(new ScopedStage(clusterStage, clusterChildren))
        })

        return stages
    }


    static void execute(String[] args) {
        def ys = new YamlSlurper()
        def configFile = new File("config/job-config.yaml")
        def jc = ys.parse(configFile)
        def env = new Environment(jc)
        env.log("Reading config from ${configFile.absolutePath}")

        def ctx = new StageContext()
        ctx.jc = jc
        ctx.env = env
        ctx.performerServer = jc.servers.performer
        ctx.dryRun = jc.settings.dryRun
        ctx.force = jc.settings.force
        String version = jcPrep(ctx, args)
        def allPerms = parseConfig(ctx)
        PerfDatabase.migrate(ctx, args)
        def db = PerfDatabase.compareRunsAgainstDb(ctx, allPerms, args)
        def parsed2 = parseConfig2(ctx, db)
        def planned = plan(ctx, parsed2, jc)
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
        try {
            root.execute(ctx)
        }finally {
            root.finish(ctx)
        }
        jcCleanup()
    //run(ctx, planned)
    //print(planned)
    }

    public static void main(String[] args) {
        execute(args)
    }
}