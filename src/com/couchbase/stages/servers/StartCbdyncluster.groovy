package com.couchbase.stages.servers

import com.couchbase.stages.Stage
import groovy.transform.CompileStatic
import com.couchbase.context.StageContext

/**
 * Starts a cluster with cbdyncluster
 */
@CompileStatic
class StartCbdyncluster extends Stage {
    private String clusterId
    private String clusterIp = "needs cbdyncluster to be setup first"
    private final int numNodes
    private final String clusterVersion
    private final int numReplicas
    private final String refreshTime

    StartCbdyncluster(int numNodes, String clusterVersion, int numReplicas, String ip, String refreshTime = "2h") {
        this.numReplicas = numReplicas
        this.clusterVersion = clusterVersion
        this.numNodes = numNodes
        this.refreshTime = refreshTime
        this.clusterIp = ip
    }

    @Override
    String name() {
        return "Start cbdyncluster $clusterVersion"
    }

    @Override
    void executeImpl(StageContext ctx) {
        //no-op
//        def imp = ctx.env
//
//        // Allocate the cluster
//        clusterId = imp.execute("cbdyncluster allocate --num-nodes=" + numNodes + " --server-version=$clusterVersion")
//        imp.log("Got cluster ID $clusterId")
//
//        //Find the cluster IP
//        def ips = imp.executeSimple("cbdyncluster ips $clusterId").trim()
//        imp.log("Got raw cluster IPs " + ips)
//        def ip = ips.tokenize(',')[0]
//        imp.log("Got cluster IP http://$ip:8091")
//        clusterIp = ip
//        def nodesInfo = ""
//        for (int i = 0; i < numNodes; i++) {
//            nodesInfo = "$nodesInfo --node kv"
//        }
//
//        imp.execute("cbdyncluster $nodesInfo setup $clusterId")
//        // Performance testing can take some time
//        imp.execute("cbdyncluster refresh $clusterId $refreshTime")
//        imp.execute("cbdyncluster add-bucket --name default --replica-count $numReplicas $clusterId")
//        imp.execute("curl -s -u Administrator:password -d flushEnabled=1 http://" + ip + ":8091/pools/default/buckets/default")
//        imp.execute("curl -s -u Administrator:password -d 'storageMode=plasma' http://" + ip + ":8091/settings/indexes")
    }

    @Override
    void finishImpl(StageContext ctx) {
        // Easy to run out of resources during iterating, so cleanup even
        // though cluster will be auto-removed after a time
//        ctx.env.execute("cbdyncluster rm $clusterId")
    }

    String clusterIp() {
        return clusterIp
    }
}