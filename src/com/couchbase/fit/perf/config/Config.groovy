package com.couchbase.fit.perf.config

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.json.JsonGenerator
import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.transform.ToString

import java.time.Duration

//import static com.couchbase.fit.perf.config.PerfConfig.Variable.PredefinedVariable.*

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
        List<Workload> workloads
    }

    @ToString(includeNames = true, includePackage = false)
    static class Variables {
        List<PredefinedVariable> predefined
        List<CustomVariable> custom
    }

    @ToString(includeNames = true, includePackage = false)
    static class PredefinedVariable {
        PredefinedVariableName name
        Object value

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
        Integer port
    }

    @ToString(includeNames = true, includePackage = false)
    static class Implementation {
        String language
        String version
        Integer port
    }

    @ToString(includeNames = true, includePackage = false)
    static class Workload {
//        String description
        List<Operation> operations
        //Transaction transaction
        //Variables variables
        
        @ToString(includeNames = true, includePackage = false)
        static class Operation {
            Op op
            String count

            @ToString(includeNames = true, includePackage = false)
            static enum Op {
                @JsonProperty("insert") INSERT,
                @JsonProperty("replace") REPLACE,
                @JsonProperty("remove") REMOVE,
                @JsonProperty("get") GET

                @Override
                String toString() {
                    return this.name().toLowerCase()
                }
            }
        }
    

        @ToString(includeNames = true, includePackage = false)
        static class Transaction {
            List<Operation> operations

            @ToString(includeNames = true, includePackage = false)
            static class Operation {
                Op op
                Doc doc
                Operation repeat
                String count

                @ToString(includeNames = true, includePackage = false)
                static enum Op {
                    @JsonProperty("insert") INSERT,
                    @JsonProperty("replace") REPLACE,
                    @JsonProperty("remove") REMOVE

                    @Override
                    String toString() {
                        return this.name().toLowerCase()
                    }
                }
            }

            static class Doc {
                From from
                Distribution distribution

                @ToString(includeNames = true, includePackage = false)
                static enum From {
                    @JsonProperty("uuid") UUID,
                    @JsonProperty("pool") POOL

                    @Override
                    String toString() {
                        return this.name().toLowerCase()
                    }
                }

                @ToString(includeNames = true, includePackage = false)
                static enum Distribution {
                    @JsonProperty("uniform") UNIFORM

                    @Override
                    String toString() {
                        return this.name().toLowerCase()
                    }
                }

                @Override
                String toString() {
                    return "doc{" +
                            "from=" + from.name().toLowerCase() +
                            (distribution == null ? "" : ", dist=" + distribution.name().toLowerCase()) +
                            '}'
                }
            }
        }
    }
}

@CompileStatic
@ToString(includeNames = true, includePackage = false)
class SetWorkload {
    PerfConfig.Workload.Transaction transaction
    SetVariables variables

    SetWorkload(PerfConfig.Workload.Transaction transaction, SetVariables variables) {
        this.transaction = transaction
        this.variables = variables
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
    PerfConfig.Variables vars
    String description
    PerfConfig.Workload workload

    def toJson() {
        Map<String, Object> jsonVars = new HashMap<>()
        vars.custom.forEach(v -> jsonVars[v.name] = v.value);
        vars.predefined.forEach(v -> jsonVars[v.name.toString()] = v.value);

        Map<String, String> clusterVars = new HashMap<>()
        clusterVars["hostname"] = cluster.hostname

        def gen = new JsonGenerator.Options()
                .excludeNulls()
                .build()

        return gen.toJson([
                "impl"     : impl,
                "vars"     : jsonVars,
                "cluster"  : clusterVars,
                "workload" : [
                        "description": description
                ],
        ])
    }
}
