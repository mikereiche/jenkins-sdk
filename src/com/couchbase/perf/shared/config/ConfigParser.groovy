package com.couchbase.perf.shared.config

import com.couchbase.context.StageContext
import com.couchbase.versions.ImplementationVersion
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

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
                for (x in workload.include) {
                    if (x.language == implementation.language) {
                        if (x.version != null) {
                            def requiredVersion = ImplementationVersion.from(x.version)
                            def sdkVersion = ImplementationVersion.from(implementation.version)
                            var include = sdkVersion.isAbove(requiredVersion) || sdkVersion == requiredVersion
                            return include
                        } else {
                            def out = implementation.language == x.language
                            if (out) {
                                ctx.env.log("Including based on language '${implementation.language}' workload ${workload}")
                                return true
                            }
                        }
                    }
                }
                return false
            }
            else {
                throw new UnsupportedOperationException()
            }
        }
        return true
    }

    /**
     * Variables can either have a single value or a range of values.
     * If it's a range, we need to calculate all possible permutations.
     */
    @CompileStatic
    static Set<Set<Variable>> calculateVariablePermutations(StageContext ctx, List<Variable> variables) {
        Set<Set<Variable>> out = new HashSet<>()

        if (variables.size() == 1) {
            var v = variables.get(0)
            if (v.values() != null) {
                v.values().forEach(value -> {
                    out.add(Set.of(new Variable(v.name(), value, null)))
                })
            } else if (v.value() != null) {
                out.add(Set.of(new Variable(v.name(), v.value(), null)))
            }

        } else {
            variables.forEach(v -> {

                var variablesWithout = new ArrayList<>(variables)
                variablesWithout.remove(v)

                var permsWithout = calculateVariablePermutations(ctx, variablesWithout)

                permsWithout.forEach(without -> {
                    if (v.values() != null) {
                        v.values().forEach(value -> {
                            var newV = new Variable(v.name(), value, null)
                            var toAdd = new HashSet(without)
                            toAdd.add(newV)
                            out.add(toAdd)
                        })
                    } else if (v.value() != null) {
                        var newV = new Variable(v.name(), v.value(), null)
                        var toAdd = new HashSet(without)
                        toAdd.add(newV)
                        out.add(toAdd)
                    }
                })
            })
        }

        return out
    }

    static Settings merge(Settings top, Settings descended) {
        var grpc = descended.grpc() == null
                ? top.grpc()
                : descended.grpc()
        var variables = new ArrayList<Variable>(descended.variables())
        top.variables().forEach(v -> {
            if (!variables.stream().anyMatch({it.name() == v.name()})) {
                variables.add(v)
            }
        })
        return new Settings(variables, grpc)
    }

    static List<Run> allPerms(StageContext ctx, PerfConfig config) {
        def out = new ArrayList<Run>()
        for (cluster in config.matrix.clusters) {
            for (impl in config.matrix.implementations) {
                for (workload in config.matrix.workloads) {

                    var merged = merge(config.settings, workload.settings())

                    var perms = calculateVariablePermutations(ctx, merged.variables())
                    perms.forEach(perm -> {
                        var newWorkload = new Workload(workload.operations(), new Settings(perm.toList(), merged.grpc()), workload.include(), workload.exclude())

                        if (includeRun(ctx, newWorkload, impl)) {
                            def run = new Run()
                            run.cluster = cluster
                            run.impl = impl
                            run.workload = newWorkload

                            out.add(run)
                        }
                    })
                }
            }
        }
        return out
    }
}
