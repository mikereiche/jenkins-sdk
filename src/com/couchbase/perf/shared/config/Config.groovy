package com.couchbase.perf.shared.config

import com.couchbase.perf.shared.config.PerfConfig.Implementation
import com.fasterxml.jackson.annotation.JsonProperty
import groovy.json.JsonGenerator
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.Immutable
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
    public final Servers servers
    public final Database database
    public final Map<String, String> executables
    public final Matrix matrix
    public final Settings settings

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
        String connection_string_driver
        String connection_string_performer
        String connection_string_driver_docker
        String connection_string_performer_docker
        String hostname_rest
        String hostname_rest_docker
        String cert_path
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

        // Only present if Protostellar
        String stellarNebulaSha

        // Any new fields here probably want adding into toJsonRaw below, and into the driver config, and includeVariablesThatApplyToThisRun

        boolean isProtostellar() {
            return connection_string_performer.startsWith("protostellar")
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
                    "topology"  : topology
            ]
            // We need to write something to the database to distinguish Protostellar & OpenShift testing.  Using the
            // performer's connection string.  It may actually be using connection_string_performer_docker, but we can't
            // know that here, and it's not relevant for this purpose.
            // There's a lot of existing tests that don't have this connectionString field, so we only check it for
            // newer tests - e.g. Protostellar ones.
            // Update: now edited the database so all tests include it.
            if (forDatabaseComparison) {
                out.put("connectionString", connection_string_performer)
            }
            if (!forDatabaseComparison) {
                out.put("connection_string_driver", connection_string_driver)
                out.put("connection_string_driver_docker", connection_string_driver_docker)
                out.put("connection_string_performer", connection_string_performer)
                out.put("connection_string_performer_docker", connection_string_performer_docker)
                out.put("hostname_rest", hostname_rest)
                out.put("hostname_rest_docker", hostname_rest_docker)
                out.put("cert_path", cert_path)
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

        // True iff "snapshot" was the originally specified version
        boolean isSnapshot

        Implementation() {}

        Implementation(String language, String version, Integer port, String sha = null, boolean isSnapshot = false) {
            this.language = language
            this.version = version
            this.port = port
            this.sha = sha
            this.isSnapshot = isSnapshot
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
@ToString(includeNames = true, includePackage = false)
class Include {
    public final Implementation implementation;
    public final PerfConfig.Cluster cluster;
}

/**
 * Read from job-config.yaml.
 *
 * Either value or values will be non-null, not both.
 *
 * At the point all variables have been permuted ready to be written to a per-run config, only name and value will be present.
 *
 * include decides whether a variable is included - usually used to specify per-SDK tunables
 */
@ToString(includeNames = true, includePackage = false)
class Variable {
    public final String name;
    public final Object value;
    // "tunable" or null
    public final String type;
    public final List<Object> values = null;
    public final List<Include> include = null;

    Variable() {}

    Variable(String name, Object value, String type) {
        this.name = name
        this.value = value
        this.type = type
    }

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

@ToString(includeNames = true, includePackage = false)
class Settings {
    public final List<Variable> variables;
    public final Object grpc;

    Settings() {}

    Settings(List<Variable> variables, Object grpc) {
        this.variables = variables
        this.grpc = grpc
    }
}

@ToString(includeNames = true, includePackage = false)
class Workload {
    Object operations;
    Settings settings;
    Object include;
    Object exclude;

    Workload() {}

    Workload(Object operations, Settings settings, Object include, Object exclude) {
        this.operations = operations
        this.settings = settings
        this.include = include
        this.exclude = exclude
    }

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
    public final PerfConfig.Implementation impl
    public final Workload workload
    public final PerfConfig.Cluster cluster

    Run(PerfConfig.Implementation impl, Workload workload, PerfConfig.Cluster cluster) {
        this.impl = impl
        this.workload = workload
        this.cluster = cluster
    }

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
