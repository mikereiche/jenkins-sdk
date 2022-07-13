package com.couchbase.perf.sdk.stages


import com.couchbase.perf.shared.config.PredefinedVariablePermutation
import com.couchbase.stages.servers.InitialiseCluster
import com.couchbase.stages.Stage
import groovy.json.JsonBuilder
import groovy.json.JsonGenerator
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.yaml.YamlBuilder
import com.couchbase.context.StageContext
import com.couchbase.perf.shared.config.PerfConfig
import com.couchbase.perf.shared.config.Run
import org.apache.groovy.yaml.util.YamlConverter

import java.time.Instant
import java.util.stream.Collectors

/**
 * Outputs the runner config
 */
@CompileStatic
class OutputPerformerConfig extends Stage {
    private final List<Run> runs
    private final String outputFilenameAbs
    private final PerfConfig.Cluster cluster
    private final PerfConfig.Implementation impl
    private final config
    private final InitialiseCluster stageCluster
    private final InitialiseSDKPerformer stagePerformer
    private final Object topLevelVariables

    OutputPerformerConfig(InitialiseCluster stageCluster,
                          InitialiseSDKPerformer stagePerformer,
                          config,
                          PerfConfig.Cluster cluster,
                          PerfConfig.Implementation impl,
                          List<Run> runs,
                          Object topLevelVariables,
                          String outputFilenameAbs) {
        this.stagePerformer = stagePerformer
        this.stageCluster = stageCluster
        this.impl = impl
        this.cluster = cluster
        this.runs = runs
        this.outputFilenameAbs = outputFilenameAbs
        this.config = config
        this.topLevelVariables = topLevelVariables
    }

    @Override
    String name() {
        return "Output performer config for ${runs.size()} runs to $outputFilenameAbs"
    }

    String outputFilenameAbs() {
        return outputFilenameAbs
    }

    @Override
    @CompileDynamic
    void executeImpl(StageContext ctx) {
        var runsAsYaml = runs.stream().map(run -> {
            def yaml = new YamlBuilder()
            yaml {
                uuid UUID.randomUUID().toString()
                operations(run.workload.operations)
                variables(run.workload.variables)
            }
            yaml.content
        }).collect(Collectors.toList())

        def gen = new JsonGenerator.Options()
            .excludeNulls()
            .build()

        if (impl.language == "python" && !(impl.version.contains("."))){
            // todo move this
            //Find most recent Python version
            // This might be an incorrect way to note down what version is being tested as it just bases it on the most recent release rather than what is being currently worked on
            String currentPythonVersion = ctx.env.executeSimple("python3 -m yolk -V couchbase | sed 's/couchbase //g'")
            String mostRecentCommit = ctx.env.executeSimple("git ls-remote https://github.com/couchbase/couchbase-python-client.git HEAD | tail -1 | sed 's/HEAD//g'")
            impl.version = "${currentPythonVersion}-${Instant.now()}-${mostRecentCommit}"
        }
        def json = new JsonBuilder(gen)
        json {
            impl impl
            connections {
                cluster cluster.toJsonRaw(false)

                performer {
                    hostname "localhost"
                    hostname_docker InitialiseSDKPerformer.CONTAINER_NAME
                    port stagePerformer.port()
                }

                database(config.database)
            }
            runs runsAsYaml
            variables topLevelVariables
        }

        def converted = YamlConverter.convertJsonToYaml(new StringReader(json.toString()))

        new File(outputFilenameAbs).write(converted)
    }
}