package com.couchbase.perf.shared.config

import com.couchbase.context.StageContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovyjarjarantlr4.v4.runtime.misc.Nullable

import java.util.stream.Collectors

@CompileStatic
class ConfigParser {
    private final static ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    private final static ObjectMapper jsonMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    static PerfConfig readPerfConfig(String filename) {
        return yamlMapper.readValue(new File(filename), PerfConfig.class)
    }

    @CompileDynamic
    static boolean includeRun(StageContext ctx, Object workload, PerfConfig.Implementation implementation) {
        if (workload.exclude == null && workload.include == null) {
            return true
        }
        else {
            // Only support *clude.implementation.language currently, could support others in future
            if (workload.exclude != null) {
                def out = implementation.language != workload.exclude.implementation.language
                if (!out) {
                    ctx.env.log("Excluding based on language '${implementation.language}' workload ${workload}")
                }
                return out
            }
            else if (workload.include != null) {
                def out = implementation.language == workload.include.implementation.language
                if (out) {
                    ctx.env.log("Including based on language '${implementation.language}' workload ${workload}")
                }
                return out
            }
            else {
                throw new UnsupportedOperationException()
            }
        }
    }
    
    static List<Run> allPerms(StageContext ctx, PerfConfig config) {
        def out = new ArrayList<Run>()
        for (cluster in config.matrix.clusters) {
            for (impl in config.matrix.implementations) {
                for (workload in config.matrix.workloads) {
                    if (includeRun(ctx, workload, impl)) {
                        def run = new Run()
                        run.cluster = cluster
                        run.impl = impl
                        run.workload = workload

                        out.add(run)
                    }
                }
            }
        }
        return out
    }
}
