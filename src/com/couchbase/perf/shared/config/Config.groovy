package com.couchbase.perf.shared.config

import com.couchbase.perf.shared.config.PerfConfig.Implementation
import com.fasterxml.jackson.annotation.JsonProperty
import groovy.json.JsonGenerator
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.ImmutableOptions
import groovy.transform.ImmutableProperties
import groovy.transform.RecordOptions
import groovy.transform.ToString
import groovy.yaml.YamlBuilder


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
    Settings settings

    @ToString(includeNames = true, includePackage = false)
    static class Matrix {
        List<Cluster> clusters
        List<Implementation> implementations
        List<Workload> workloads
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

        // "protostellar" or null (haven't set "couchbase" as it would require rerunning everything)
        String scheme

        // Only present if Protostellar
        String stellarNebulaSha

        // Any new fields here probably want adding into toJsonRaw below, and into the driver config, and includeVariablesThatApplyToThisRun

        boolean isProtostellar() {
            return scheme == "protostellar"
        }

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
                    "region"    : region,
                    "scheme"    : scheme,
            ]
            if (!forDatabaseComparison) {
                out.put("hostname", hostname)
                out.put("hostname_docker", hostname_docker)
            }
            if (isProtostellar()) {
                out.put("stellarNebulaSha", stellarNebulaSha)
            }
            return out
        }
    }

    @ToString(includeNames = true, includePackage = false)
    static class Implementation {
        // "Java"
        String language

        // "3.3.3" or "3.3.3-6abad3" or "refs/changes/94/184294/1"
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

        boolean isGerrit() {
            return version.startsWith("refs/")
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

/**
 * Whether a given thing should be included in this run.
 *
 * Includes are AND-based - all Includes must be satisfied for the run to be included.
 */
record Include(Implementation implementation,
               PerfConfig.Cluster cluster) {}

/**
 * Read from job-config.yaml.
 *
 * Either value or values will be non-null, not both.
 *
 * At the point all variables have been permuted ready to be written to a per-run config, only name and value will be present.
 *
 * include decides whether a variable is included - usually used to specify per-SDK tunables
 */
@ImmutableOptions
record Variable(String name,
                Object value,
                // "tunable" or null
                String type = null,
                List<Object> values = null,
                List<Include> include = null) {
    // By this point the variables have been permuted and only value is present
    @CompileDynamic
    def asYaml() {
        return [
                name: this.name,
                value: this.value,
                type: this.type,
        ]
    }
}

@ImmutableOptions
record Settings(List<Variable> variables, Object grpc) {}

@ImmutableOptions
record Workload(Object operations,
                Settings settings,
                Object include,
                Object exclude) {
    @CompileDynamic
    def toJson() {
        // Some workload variables are used for meta purposes but we don't want to compare the database runs with them
        return [
                "operations": operations
        ]
    }
}

@CompileStatic
@ToString(includeNames = true, includePackage = false)
class Run {
    PerfConfig.Implementation impl
    Workload workload
    PerfConfig.Cluster cluster

    @CompileDynamic
    def toJson() {
        Map<String, Object> jsonVars = new HashMap<>()
        if (workload.settings() != null && workload.settings().variables() != null) {
            workload.settings().variables().forEach(var -> {
                jsonVars.put(var.name(), var.value())
            })
        }

        jsonVars.put("driverVer", 6)
        int performerVersion = 1
        jsonVars.put("performerVer", performerVersion)

        def gen = new JsonGenerator.Options()
                .excludeNulls()
                .build()

        // This is for comparison to the database, which isn't a 1:1 match to the config YAML, since some stuff
        // gets removed and flattened out to simplify the database JSON.  For example, no need to record GRPC settings.
        def out = gen.toJson([
                "impl"    : impl.toJson(),
                "vars"    : jsonVars,
                "cluster" : cluster.toJsonRaw(true),
                "workload": workload.toJson(),
        ])
        return out
    }
}
