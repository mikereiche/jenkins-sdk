package com.couchbase.perf.shared.config


import com.fasterxml.jackson.annotation.JsonProperty
import groovy.json.JsonGenerator
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.ToString


/**
 * The parsed job-config.yaml.
 *
 * Try to parse the minimum required into objects, as we currently have very similar code here and in the driver
 * (which has to parse a similar per-run config), and it's brittle.  Aim to just parse through the YAML into the per-run
 * config as much as possible.
 */
@CompileStatic
@ToString(includeNames = true, includePackage = false)
class PerfConfig {
    Servers servers
    Database database
    Map<String, String> executables
    Matrix matrix

    @ToString(includeNames = true, includePackage = false)
    static class Matrix {
        List<Cluster> clusters
        List<Implementation> implementations
        List<Object> workloads
    }

    @ToString(includeNames = true, includePackage = false)
    static class Servers {
        String performer
    }

    @ToString(includeNames = true, includePackage = false)
    static class Database {
        String host
        int port
        String user
        String password
        String database
    }

    // This is the superset of all supported cluster params.  Not all of them are used for each cluster type.
    @ToString(includeNames = true, includePackage = false)
    static class Cluster {
        String version
        Integer nodeCount
        Integer memory
        Integer cpuCount
        String type
        String hostname
        String hostname_docker
        String storage
        Integer replicas

        // "c5.4xlarge"
        // Only present on AWS
        String instance

        // "disabled"
        String compaction

        // By topology we mean where the performer, driver and cluster are running.  There are many possible permuations
        // so we represent them with a code letter.
        // "A" = driver, performer and cluster all running on same AWS node, in docker
        String topology

        // "us-east-2"
        // Only present on AWS
        String region

        // Any new fields here probably want adding into toJsonRaw below, and into the driver config

        @CompileDynamic
        def toJsonRaw(boolean forDatabaseComparison) {
            def out = [
                    "version"   : version,
                    "nodeCount" : nodeCount,
                    "memory"    : memory,
                    "cpuCount"  : cpuCount,
                    "type"      : type,
                    "storage"   : storage,
                    "replicas"  : replicas,
                    "instance"  : instance,
                    "compaction": compaction,
                    "topology"  : topology,
                    "region"    : region
            ]
            if (!forDatabaseComparison) {
                out.put("hostname", hostname)
                out.put("hostname_docker", hostname_docker)
            }
            return out
        }
    }

    @ToString(includeNames = true, includePackage = false)
    static class Implementation {
        // "Java"
        String language
        // "3.3.3" or "3.3.3-6abad3"
        String version
        // "6abad3", nullable - used for some languages to handle snapshot builds
        String sha
        // A null port means jenkins-sdk needs to bring it up
        Integer port

        Implementation() {}

        Implementation(String language, String version, Integer port, String sha = null) {
            this.language = language
            this.version = version
            this.port = port
            this.sha = sha
        }

        @CompileDynamic
        def toJson() {
            return [
                    "language": language,
                    "version" : version
            ]
        }
    }
}

@ToString(includeNames = true, includePackage = false)
class PredefinedVariablePermutation {
    String name
    Object value

    PredefinedVariablePermutation(String name, Object value){
        this.name = name
        this.value = value
    }

    enum PredefinedVariableName {
        @JsonProperty("horizontal_scaling") HORIZONTAL_SCALING,
        @JsonProperty("doc_pool_size") DOC_POOL_SIZE,
        @JsonProperty("durability") DURABILITY

        String toString() {
            return this.name().toLowerCase()
        }
    }
}

@CompileStatic
@ToString(includeNames = true, includePackage = false)
class Run {
    PerfConfig.Cluster cluster
    PerfConfig.Implementation impl
    Object workload

    @CompileDynamic
    def toJson() {
        Map<String, Object> jsonVars = new HashMap<>()
        if (workload.variables != null) {
            if (workload.variables.custom != null) {
                workload.variables.custom.forEach(var -> jsonVars.put(var.name, var.value))
            }
            if (workload.variables.predefined != null) {
                workload.variables.predefined.forEach(var -> {
                    if (var.name == "horizontalScaling") {
                        jsonVars.put("horizontal_scaling", var.values[0])
                    }
                    else {
                        jsonVars.put(var.name, var.values[0])
                    }
                })
            }
        }

        // A driver bug is writing these as strings.
        jsonVars.put("driverVer", 6)
        int performerVersion = 1
        jsonVars.put("performerVer", performerVersion)

        def gen = new JsonGenerator.Options()
                .excludeNulls()
                .build()

        // Some workload variables are used for meta purposes but we don't want to compare the database runs with them
        def copiedWorkload = workload.clone()
        copiedWorkload.variables = null
        copiedWorkload.include = null
        copiedWorkload.exclude = null

        return gen.toJson([
                "impl"    : impl.toJson(),
                "vars"    : jsonVars,
                "cluster" : cluster.toJsonRaw(true),
                "workload": copiedWorkload,
        ])
    }
}
