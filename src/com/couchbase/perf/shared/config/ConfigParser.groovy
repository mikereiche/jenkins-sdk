package com.couchbase.perf.shared.config

import com.couchbase.context.StageContext
import com.couchbase.perf.shared.config.PerfConfig.Cluster
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

    /**
     * Whether a possible run should actually be included.
     */
    @CompileDynamic
    static boolean includeRun(int idx, int count, StageContext ctx, PermutedWorkload workload, PerfConfig.Implementation implementation, PerfConfig.Cluster cluster) {

        if (idx % 10000 == 1) {
            ctx.env.log("${idx} of ${count} ${workload} ${implementation} ${cluster}")
        }

        boolean exclude = false
        var excludeReasons = []

        if (cluster.isProtostellar()) {
            // Currently only specific SDKs can run Protostellar
            if (implementation.language != "Java") {
                exclude = true
                excludeReasons.add("SDK ${implementation.language} does not support Protostellar")
            }

            try {
                def ver = ImplementationVersion.from(implementation.version)
                if (ver.isBelow(ImplementationVersion.from("3.4.6"))) {
                    exclude = true
                    excludeReasons.add("Java SDK ${implementation.version} does not support current Protostellar")
                }
            }
            catch (err) {
                exclude = true
                excludeReasons.add("Something went wrong: ${err}")
            }
        }

        // Excludes are OR-ed.  E.g. if any matches, it's excluded.
        if (workload.exclude() != null) {
            for (x in workload.exclude()) {
                if (x.language == implementation.language) {
                    if (x.version != null) {
                        if (x.version == implementation.version) {
                            excludeReasons.add("Excluding based on version ${x.version} ${implementation.version}")
                            exclude = true
                        }
                    } else {
                        if (implementation.language != x.language) {
                            excludeReasons.add("Excluding based on language ${x.language} ${implementation.language}")
                            exclude = true
                        }
                    }
                }
                if (x.protostellar !== null) {
                    if (cluster.isProtostellar() == x.protostellar) {
                        excludeReasons.add("Excluding because protostellar ${cluster.isProtostellar()} != ${x.protostellar}")
                        exclude = true
                    }
                }
            }
        }

        // Includes are OR-ed.  E.g. if any matches, it's included.
        if (workload.include() != null) {
            def excludedByInclude = true

            for (x in workload.include()) {
                // Missing language field will match all languages (useful for e.g. running against "snapshot" on all SDKs)
                if (x.language == implementation.language || x.language == null) {
                    if (x.version != null) {
                        if (x.version.startsWith("refs/")) {
                            if (x.version == implementation.version) {
                                excludedByInclude = false
                            }
                        }
                        else if (x.version == "snapshot") {
                            if (implementation.isSnapshot) {
                                excludedByInclude = false
                            }
                        }
                        else {
                            def requiredVersion = ImplementationVersion.from(x.version)
                            if (!implementation.isGerrit()) {
                                def sdkVersion = ImplementationVersion.from(implementation.version)
                                var include = sdkVersion.isAbove(requiredVersion) || sdkVersion == requiredVersion
                                if (include) {
                                    excludedByInclude = false
                                }
                            }
                        }
                    } else {
                        // Missing version field will match all versions.
                        excludedByInclude = false
                    }
                }

                if (x.protostellar != null) {
                    if (cluster.isProtostellar() == x.protostellar) {
                        excludedByInclude = false
                    }
                }

                if (x.api != null) {
                    def api = workload.settings().variables().find {it.value() == "api" }
                    if (api != null) {
                        excludedByInclude = !(x.api == api)
                    }
                }
            }

            if (excludedByInclude) {
                exclude = true
                excludeReasons.add("There was an include section that did not match this")
            }
        }

        // (Currently) need hardcoded logic to filter the APIs to what the performer supports, rather than using
        // performerCapsFetch - see CBD-5264.
        var api = workload.settings().variables().find { it.name == "api" }
        if (api != null) {
            boolean isOne = api.value() == "DEFAULT"
            boolean isTwo = api.value() == "ASYNC"
            boolean isThree = api.value() == "THREE"

            boolean isTransactionWorkload = workload.operations().get(0).op == "transaction"

            boolean supportsOne = true
            boolean supportsTwo = implementation.language in ["Java"]
            boolean supportsThree = implementation.language in ["Java"]

            // Java transactions does not have an async API
            if (isTransactionWorkload && implementation.language == "Java") {
                supportsTwo = false
            }

            if ((isOne && !supportsOne) || (isTwo && !supportsTwo) || (isThree && !supportsThree)) {
                exclude = true
                excludeReasons.add("SDK ${implementation.language} does not support API ${api.value()}")
            }
        }

        if (exclude) {
//            ctx.env.log("Excluding ${implementation.language} ${implementation.version} run ${workload} cluster ${cluster} because:")
//            excludeReasons.forEach(er -> ctx.env.log("   ${er}"))
        }

        return !exclude
    }

    /**
     * Variables can either have a single value or a range of values.
     * If it's a range, we need to calculate all possible permutations.
     */
    @CompileStatic
    static Set<Set<PermutedVariable>> calculateVariablePermutations(StageContext ctx, List<Variable> variables) {
        Set<Set<PermutedVariable>> out = new HashSet<>()

        if (variables.size() == 1) {
            var v = variables.get(0)
            if (v.values != null) {
                v.values.forEach(value -> {
                    // null values are filtered out for good reasons that I now forget...
                    if (value != null) {
                        def toAdd = new HashSet<PermutedVariable>()
                        toAdd.add(new PermutedVariable(v.name, value, v.type))
                        out.add(toAdd)
                    }
                })
            } else if (v.value != null) {
                def toAdd = new HashSet<PermutedVariable>()
                toAdd.add(new PermutedVariable(v.name, v.value, v.type))
                out.add(toAdd)
            }

        } else {
            variables.forEach(v -> {

                var variablesWithout = new ArrayList<>(variables)
                variablesWithout.remove(v)

                var permsWithout = calculateVariablePermutations(ctx, variablesWithout)

                permsWithout.forEach(without -> {
                    if (v.values != null) {
                        v.values.forEach(value -> {
                            if (value != null) {
                                var newV = new PermutedVariable(v.name, value, v.type)
                                var toAdd = new HashSet<PermutedVariable>(without)
                                toAdd.add(newV)
                                out.add(toAdd)
                            }
                        })
                    } else if (v.value != null) {
                        var newV = new PermutedVariable(v.name, v.value, v.type)
                        var toAdd = new HashSet<PermutedVariable>(without)
                        toAdd.add(newV)
                        out.add(toAdd)
                    }
                })
            })
        }

        return out
    }

    /**
     * Merges the top-level settings with the workload's settings.
     */
    static Settings merge(Settings top, Settings descended) {
        var grpc = descended.grpc == null
                ? top.grpc
                : descended.grpc
        var variables = new ArrayList<Variable>(descended.variables)
        top.variables.forEach(v -> {
            if (!variables.stream().anyMatch({it.name == v.name})) {
                variables.add(v)
            }
        })
        return new Settings(variables, grpc)
    }

    static List<Run> allPerms(StageContext ctx, PerfConfig config) {
        def out = new ArrayList<Run>()

        def workloadCount = 0

        for (cluster in config.matrix.clusters) {
            for (impl in config.matrix.implementations) {
                for (workload in config.matrix.workloads) {
                    var merged = merge(config.settings, workload.settings)
                    var variablesThatApply = includeVariablesThatApplyToThisRun(ctx, cluster, impl, merged.variables)
                    var perms = calculateVariablePermutations(ctx, variablesThatApply)
                    perms.forEach(perm -> {
                        // ctx.env.log("Potential permuations (not all will be run): ${config.matrix.clusters.size()} clusters * ${config.matrix.implementations.size()} implementations * ${config.matrix.workloads.size()} workloads * ${perms.size()} variable permuations")
                        ctx.env.log("${cluster.connection_string_driver} ${impl} ${workload} ${perm}")
                        workloadCount += 1
                    })
                }
            }
        }

        ctx.env.log("Found ${workloadCount} potential runs, filtering...")

        def idx = 0

        for (cluster in config.matrix.clusters) {
            for (impl in config.matrix.implementations) {
                for (workload in config.matrix.workloads) {

                    var merged = merge(config.settings, workload.settings)

                    var variablesThatApply = includeVariablesThatApplyToThisRun(ctx, cluster, impl, merged.variables)

                    var perms = calculateVariablePermutations(ctx, variablesThatApply)
                    perms.forEach(perm -> {
                        var newWorkload = new PermutedWorkload(workload.operations, new PermutedSettings(perm.toList(), merged.grpc), workload.include, workload.exclude)

                        if (includeRun(idx++, workloadCount, ctx, newWorkload, impl, cluster)) {
                            def run = new Run(impl, newWorkload, cluster)
                            out.add(run)
                        }
                    })
                }
            }
        }

        ctx.env.log("Filtered ${workloadCount} potential runs down to ${out.size()} actual runs, now checking which have already been run")

        return out
    }

    static List<Variable> includeVariablesThatApplyToThisRun(StageContext stageContext,
                                                             Cluster cluster,
                                                             PerfConfig.Implementation implementation,
                                                             List<Variable> variables) {
        return variables.findAll {
            if (it.include == null) {
                return true
            }

            var ret = true

            for (def inc in it.include) {
                if (inc.implementation != null) {
                    if (inc.implementation.language != implementation.language
                            || (inc.implementation.version != null && inc.implementation.version != implementation.version)) {
                        ret = false
                    }
                }

                if (inc.cluster != null) {
                    if (inc.cluster.version != null && inc.cluster.version != cluster.version) ret = false
                    if (inc.cluster.nodeCount != null && inc.cluster.nodeCount != cluster.nodeCount) ret = false
                    if (inc.cluster.memory != null && inc.cluster.memory != cluster.memory) ret = false
                    if (inc.cluster.cpuCount != null && inc.cluster.cpuCount != cluster.cpuCount) ret = false
                    if (inc.cluster.type != null && inc.cluster.type != cluster.type) ret = false
                    if (inc.cluster.storage != null && inc.cluster.storage != cluster.storage) ret = false
                    if (inc.cluster.replicas != null && inc.cluster.replicas != cluster.replicas) ret = false
                    if (inc.cluster.instance != null && inc.cluster.instance != cluster.instance) ret = false
                    if (inc.cluster.compaction != null && inc.cluster.compaction != cluster.compaction) ret = false
                    if (inc.cluster.topology != null && inc.cluster.topology != cluster.topology) ret = false
                    if (inc.cluster.stellarNebulaSha != null && inc.cluster.stellarNebulaSha != cluster.stellarNebulaSha) ret = false
                }
            }

            return ret
        }
    }
}
