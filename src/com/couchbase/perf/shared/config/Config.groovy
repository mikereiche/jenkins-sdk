package com.couchbase.perf.shared.config


import com.fasterxml.jackson.annotation.JsonProperty
import groovy.json.JsonGenerator
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
    Variables variables
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
    static class Variables {
        List<PredefinedVariable> predefined
        List<CustomVariable> custom
    }

    @ToString(includeNames = true, includePackage = false)
    static class PredefinedVariable {
        PredefinedVariableName name
        List<Object> values

        enum PredefinedVariableName {
            @JsonProperty("horizontal_scaling") HORIZONTAL_SCALING,
            @JsonProperty("doc_pool_size") DOC_POOL_SIZE,
            @JsonProperty("durability") DURABILITY

            String toString() {
                return this.name().toLowerCase()
            }
        }
    }

    @ToString(includeNames = true, includePackage = false)
    static class CustomVariable {
        String name
        Object value
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
        Integer nodes
        Integer replicas
        String type
        String source
        String hostname
        String hostname_docker
        Integer port
    }

    @ToString(includeNames = true, includePackage = false)
    static class Implementation {
        String language
        String version
        Integer port
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

@ToString(includeNames = true, includePackage = false)
class SetVariables {
    List<SetPredefinedVariable> predefined
    List<SetCustomVariable> custom

    SetVariables(List<SetPredefinedVariable> predefined, List<SetCustomVariable> custom) {
        this.predefined = predefined
        this.custom = custom
    }

    Integer getCustomVarAsInt(String varName) {
        if (varName.startsWith('$')) {
            return getCustomVarAsInt(varName.substring(1))
        }

        var match = custom.stream().filter(v -> v.name.equals(varName)).findFirst()
        return match
                .map(v -> (Integer) v.value)
                .orElseThrow(() -> new IllegalArgumentException("Custom variable " + varName + " not found"))
    }

    Integer horizontalScaling() {
        return (Integer) predefinedVar(PredefinedVariableName.HORIZONTAL_SCALING)
    }

    Integer docPoolSize() {
        return (Integer) predefinedVar(PredefinedVariableName.DOC_POOL_SIZE)
    }

    String durability() {
        var raw = (String) predefinedVar(PredefinedVariableName.DURABILITY)
        return raw
    }

    private Object predefinedVar(PerfConfig.PredefinedVariable.PredefinedVariableName name) {
        return predefined.stream()
                .filter(v -> v.name == name.name())
                .findFirst()
                .map(v -> v.value)
                .orElseThrow(() -> new IllegalArgumentException("Predefined variable " + name + " not found"))
    }
}

// Helper interface that lets us generically treat PredefinedVariable and CustomVariable with same code
interface HasName {
    String getName();
}

@ToString(includeNames = true, includePackage = false)
class SetPredefinedVariable implements HasName {
    PerfConfig.PredefinedVariable.PredefinedVariableName name
    Object value

    SetPredefinedVariable(PerfConfig.PredefinedVariable.PredefinedVariableName name, Object value) {
        this.name = name
        this.value = value
    }

    @Override
    String getName() {
        return name.toString()
    }
}

@ToString(includeNames = true, includePackage = false)
class SetCustomVariable implements HasName {
    String name
    Object value

    SetCustomVariable(String name, Object value) {
        this.name = name
        this.value = value
    }

    @Override
    String getName() {
        return name
    }
}


@CompileStatic
@ToString(includeNames = true, includePackage = false)
class Run {
    PerfConfig.Cluster cluster
    PerfConfig.Implementation impl
    //TODO passing in predefined variables twice, find a better way to do this
    PerfConfig.Variables vars
    List<PredefinedVariablePermutation> predefined
    Object workload

    def toJson() {
        Map<String, Object> jsonVars = new HashMap<>()
        vars.custom.forEach(v -> jsonVars[v.name] = v.value)
        predefined.forEach(v -> jsonVars[v.name.toString()] = v.value)

        Map<String, String> clusterVars = new HashMap<>()
        if(cluster.type == "gocaves"){
            String hostname = "$cluster.hostname:$cluster.port"
            clusterVars["hostname"] = hostname
        }
        else{
            clusterVars["hostname"] = cluster.hostname
        }

        def gen = new JsonGenerator.Options()
                .excludeNulls()
                .build()

        return gen.toJson([
                "impl"     : impl,
                "vars"     : jsonVars,
                "cluster"  : clusterVars,
                "workload" : workload,
        ])
    }
}
