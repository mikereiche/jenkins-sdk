def clusterHostname = ''
def done = false
def clusterReady = false

stage("run") {
    // This will use a fresh AWS instance
    parallel cluster: {
        node("qe-docker") {
            sh(script: "sudo hostname -i")
            clusterHostname = sh(script: "curl http://169.254.169.254/latest/meta-data/public-ipv4", returnStdout: true)
            echo "Connect to cluster on http://${clusterHostname}:8091"
            sh(script: "docker run -d --name cbs -p 8091-8096:8091-8096 -p 11210-11211:11210-11211 couchbase")
            // Wait for container to come up
            sh(script: "sleep 10")
            // AWS nodes have 16GB available and we see severe performance degradation when trying to give just 4GB to the cluster.  Trying 16GB.
            // Get 'Total quota (16000MB) exceeds the maximum allowed quota (14720MB)'
            script {
                def memoryQuota = 14000
                echo sh(script: "docker exec cbs opt/couchbase/bin/couchbase-cli cluster-init -c localhost --cluster-username Administrator --cluster-password password --services data,index,query --cluster-ramsize ${memoryQuota}", returnStdout: true)
                echo sh(script: "docker exec cbs opt/couchbase/bin/couchbase-cli bucket-create -c localhost --username Administrator --password password --bucket default --bucket-type couchbase --bucket-ramsize ${memoryQuota}", returnStdout: true)
                echo sh(script: "curl -u Administrator:password http://localhost:8091/pools/default -d memoryQuota=${memoryQuota}")
            }

            // dir("gocaves"){
            //     checkout([
            //         $class: 'GitSCM',
            //         branches: [[name: "force-port-test"]],
            //         userRemoteConfigs: [[url: 'https://github.com/charlie-hayes/gocaves.git']]])
            // }

            clusterReady = true
            echo "Cluster waiting until told to shut down"

            script {
                while (!done) {
                    // This logs so call it infrequently
                    sleep(time: 20, unit: 'SECONDS')
                }
            }

            echo "Cluster shutting down"
        }
    },
    driver: {
        // This will use a 2nd fresh AWS instance
        node("qe-docker") {
            try {
                sh(script: "sudo yum install -y yum-utils python")
                sh(script: "docker network create perf")

                dir("perf-sdk") {
                    checkout([
                        $class: 'GitSCM',
                        branches: [[name: "main"]],
                        userRemoteConfigs: [[url: 'https://github.com/couchbaselabs/perf-sdk.git']]])
                }

                dir("jenkins-sdk") {
                    checkout([
                        $class: 'GitSCM',
                        userRemoteConfigs: [[url: 'https://github.com/couchbaselabs/jenkins-sdk']]])
                    // todo get this working again
                    //if (params.JOB_CONFIG_OVERRIDE != null) {
                    //    echo sh(script: "curl ${params.JOB_CONFIG_OVERRIDE} --output config/job-config.yaml", returnStdout:true)
                    //}

                    // sed: look for the start of the cluster section; !b stops processing that regex; n moves to the next line; replace the contents
                    sh(script: "sed -i '/- type: unmanaged/!b;n;n;n;c\\      hostname_docker: ${clusterHostname}' config/job-config.yaml")

                    sh(script: "chmod +x gradlew")
                    sh(script: "./gradlew shadowJar")

                    echo "Waiting until cluster is ready"

                    script {
                        while (!clusterReady) {
                            sleep(time: 5, unit: 'SECONDS')
                        }
                    }

                    withCredentials([string(credentialsId: 'TIMEDB_PWD', variable: 'TIMEDB_PWD')]) {
                        echo sh(script: 'java -jar ./build/libs/jenkins2-1.0-SNAPSHOT-all.jar "$TIMEDB_PWD"')
                    }
                }
            }
            finally {
                echo "Cluster can shut down now"
                script {
                    done = true
                }
            }
        }
    }
}

// Disabling as requires an agent and currently isn't proving useful enough to justify that (plus is from older declarative pipeline version)
//    post {
//        // Archive so we can see all the logs after the AWS nodes have gone
//        always {
//            archiveArtifacts artifacts: '**', allowEmptyArchive: true
//        }
//    }
