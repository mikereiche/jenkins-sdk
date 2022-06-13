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


class Execute {
    static void jcPrep(StageContext ctx, String[] args){
        //Get timescaledb password from jenkins credential
        String dbPwd = ""
        if (args.length > 0) {
            dbPwd = args[0]
            ctx.jc.database.password = args[0]
        }

//        //Find most recent Python version
//        // This might be an incorrect way to note down what version is being tested as it just bases it on the most recent release rather than what is being currently worked on
//        String currentPythonVersion = ctx.env.executeSimple("python3 -m yolk -V couchbase | sed 's/couchbase //g'")
//        String mostRecentCommit = ctx.env.executeSimple("git ls-remote https://github.com/couchbase/couchbase-python-client.git HEAD | tail -1 | sed 's/HEAD//g'")
//        ctx.env.log("Found: ${currentPythonVersion}-${mostRecentCommit}")


        def jobConfig = new File("config/job-config.yaml")
        def lines = jobConfig.readLines()
        def addImpl = false
        def changePwd = false

        jobConfig.write("")

        //TODO This is bad, currently we read job config twice. Once to write to it and once to put it in to the PerfConfig class
        // we should Ideally be putting any new implementations into the class after it has been written.
        // I tried to do this but was getting some errors when creating a constructor for PerfConfig.Implementation.
        for (int i = 0; i < lines.size(); i++) {
            def line = lines[i]

//            if (addImpl) {
//                jobConfig.append("    - language: python\n")
//                jobConfig.append("      version: ${currentPythonVersion}-${mostRecentCommit}\n")
//                jobConfig.append(line + "\n")
//                addImpl = false
//            } else if (line.contains("  implementations:")) {
//                addImpl = true
//                jobConfig.append(line + "\n")
            if (line.contains("database:")){
                changePwd = true
                jobConfig.append(line + "\n")
            //&& dbPwd != ""
            } else if (changePwd && line.contains("password") && dbPwd != ""){
                jobConfig.append("  password: " + dbPwd + "\n")
                changePwd = false
            } else {
                jobConfig.append(line + "\n")
            }
        }
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

    static List<Stage> plan(StageContext ctx, Map<PerfConfig.Cluster, List<Run>> input, jc) {
        def stages = new ArrayList<Stage>()

        input.forEach((cluster, runsForCluster) -> {
            def clusterStage = new InitialiseCluster(cluster)
            def clusterChildren = new ArrayList<Stage>()

            def groupedByPerformer = runsForCluster.stream()
                    .collect(groupingBy((Run run) -> run.impl))

            ctx.env.log("Cluster ${cluster} requires ${groupedByPerformer.size()} performers")

            groupedByPerformer.forEach((performer, runsForClusterAndPerformer) -> {
                def groupedByPredefined = runsForClusterAndPerformer.stream()
                        .collect(groupingBy((Run run) -> run.predefined))
                groupedByPredefined.forEach((variable, runsForClusterPerformerPre) ->{
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
                            runsForClusterPerformerPre,
                            variable,
                            configFilename)
                    performerRuns.add(output)
                    performerRuns.add(new RunRunner(clusterStage, performerStage, output))

                    clusterChildren.add(new ScopedStage(performerStage, performerRuns))
                })
            })

            stages.add(new ScopedStage(clusterStage, clusterChildren))
        })

        return stages
    }


    static void execute(String[] args) {
        def ys = new YamlSlurper()
        def jc = ys.parse(new File("config/job-config.yaml"))
        def env = new EnvironmentLocal(jc.environment)

        def ctx = new StageContext()
        ctx.jc = jc
        ctx.env = env
        ctx.performerServer = jc.servers.performer
        ctx.dryRun = jc.settings.dryRun
        ctx.force = jc.settings.force
        jcPrep(ctx, args)
        def allPerms = parseConfig(ctx)
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