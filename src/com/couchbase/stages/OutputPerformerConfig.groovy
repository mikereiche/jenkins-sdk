package com.couchbase.stages

import groovy.json.JsonBuilder
import groovy.json.JsonGenerator
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.yaml.YamlBuilder
import com.couchbase.context.StageContext
import com.couchbase.fit.perf.config.PerfConfig
import com.couchbase.fit.perf.config.Run
import com.couchbase.fit.stages.BuildDockerJavaFITPerformer
import com.couchbase.fit.stages.StartDockerImagePerformer
import org.apache.groovy.yaml.util.YamlConverter

import java.util.stream.Collectors

/**
 * Outputs the runner config
 */
@CompileStatic
class OutputPerformerConfig extends Stage {
    private final List<Run> runs
    private final String outputFilename
    private String absoluteOutputFilename = null
    private final PerfConfig.Cluster cluster
    private final PerfConfig.Implementation impl
    private final config
    private final InitialiseCluster stageCluster
    private final InitialisePerformer stagePerformer

    OutputPerformerConfig(InitialiseCluster stageCluster,
                          InitialisePerformer stagePerformer,
                                  config,
                          PerfConfig.Cluster cluster,
                          PerfConfig.Implementation impl,
                          List<Run> runs,
                          String outputFilename) {
        this.stagePerformer = stagePerformer
        this.stageCluster = stageCluster
        this.impl = impl
        this.cluster = cluster
        this.runs = runs
        this.outputFilename = outputFilename
        this.config = config
    }

    @Override
    String name() {
        return "Output performer config for ${runs.size()} runs to $outputFilename"
    }

    String absoluteConfigFilename() {
        return absoluteOutputFilename
    }

    String outputFilename() {
        return outputFilename
    }

    @Override
    @CompileDynamic
    void executeImpl(StageContext ctx) {
        var runsAsYaml = runs.stream().map(run -> {
            def yaml = new YamlBuilder()
            yaml {
                uuid UUID.randomUUID().toString()
                description run.description
                operations(run.workload.operations)
            }
            yaml.content
        }).collect(Collectors.toList())

        def gen = new JsonGenerator.Options()
            .excludeNulls()
            .build()
        def json = new JsonBuilder(gen)

        json {
            impl impl
            variables(config.variables)
            connections {
                cluster {
                    hostname stageCluster.hostname()
                    username 'Administrator'
                    password 'password'
                }

                performer {
                    hostname stagePerformer.hostname()
                    port stagePerformer.port()
                }

                database(config.database)
            }
            runs runsAsYaml
        }

        def converted = YamlConverter.convertJsonToYaml(new StringReader(json.toString()))

        ctx.inSourceDir {
            absoluteOutputFilename = ctx.env.currentDir() + "/" + outputFilename
            new File(absoluteOutputFilename).write(converted)
        }
    }
}