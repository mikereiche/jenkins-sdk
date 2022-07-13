package com.couchbase.stages.servers

import com.couchbase.stages.Stage
import groovy.transform.CompileStatic
import com.couchbase.context.StageContext
import com.couchbase.perf.shared.config.PerfConfig

/**
 * Initialises a cluster based on config settings
 */
@CompileStatic
class InitialiseCluster extends Stage {
    private PerfConfig.Cluster cluster
    private List<Stage> stages = []

    InitialiseCluster(PerfConfig.Cluster cluster) {
        this.cluster = cluster
        if (cluster.type == "unmanaged") {
            // no-op
        }
        // currently doesn't work with cbdyncluster
//        else if (cluster.type == "cbdyncluster") {
//            Stage stage = new StartCbdyncluster(cluster.nodes, cluster.version, cluster.replicas)
//            stages.add(stage)
//        }
//        else if (cluster.type == "gocaves") {
//
//            // Stage stage = new StartGocaves(cluster.source, cluster.port, cluster.hostname)
//            // stages.add(stage)
//        }
        else {
            throw new IllegalArgumentException("Unknown cluster type ${cluster.type}")
        }
    }

    @Override
    String name() {
        return "Start cluster $cluster"
    }

    @Override
    List<Stage> stagesPre(StageContext ctx) {
        return stages
    }

    @Override
    void executeImpl(StageContext ctx) {}

    String hostname() {
        if (cluster.type == "unmanaged") {
            return cluster.hostname
        } else if (cluster.type == "gocaves") {
            return ((StartGocaves) stages[0]).clusterIp()
        } else {
            throw new IllegalArgumentException("Unknown cluster type ${cluster.type}")
        }
    }

    String type() {
        return cluster.type
    }

    String hostname_docker() {
        return cluster.hostname_docker
    }
}
