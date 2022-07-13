def runAWS(String command) {
    return sh(script: "aws ${command}", returnStdout: true)
}

def runSSH(String ip, String command, boolean returnStdout = false) {
    sh(script: 'ssh -o "StrictHostKeyChecking=no" -i $SSH_KEY_PATH ec2-user@' + ip + " '" + command + "'", returnStdout: returnStdout)
}

def ignore(String command) {
    try {
        sh(script: command)
    }
    catch (ignored) {}
}

void setupPrerequisitesCentos8() {
    // sh(script: "sudo ip a")
    sh(script: "cd /etc/yum.repos.d")
    // https://techglimpse.com/failed-metadata-repo-appstream-centos-8/
    sh(script: "sudo sed -i 's/mirrorlist/#mirrorlist/g' /etc/yum.repos.d/CentOS-*")
    sh(script: "sudo sed -i 's|#baseurl=http://mirror.centos.org|baseurl=http://vault.centos.org|g' /etc/yum.repos.d/CentOS-*")
    sh(script: "sudo yum install awscli -y -q")
}

void setupPrerequisitesMac() {
    sh(script: 'curl "https://awscli.amazonaws.com/AWSCLIV2.pkg" -o "AWSCLIV2.pkg"')
    sh(script: 'sudo installer -pkg AWSCLIV2.pkg -target /')
}

stage("run") {

//    node("build-sdk-macosx-11.0") {
//        String agentIp = sh(script: "ipconfig getifaddr en0", returnStdout: true).trim()
//        echo "ssh couchbase@${agentIp}"
////    }

    node("sdkqe") {
        withAWS(credentials: 'aws-sdkqe') {
            withCredentials([file(credentialsId: 'abbdb61e-d9b7-47ea-b160-7ff840c97bda', variable: 'SSH_KEY_PATH')]) {
                withCredentials([string(credentialsId: 'TIMEDB_PWD', variable: 'TIMEDB_PWD')]) {

                    setupPrerequisitesCentos8()
//                    setupPrerequisitesMac()

//                    String instanceType = "c5.large" // for cheaper iteration
                    String instanceType = "c5.4xlarge"
                    String region = "us-east-2" // cheapest
                    String instanceId = runAWS("ec2 run-instances --image-id ami-02d1e544b84bf7502 --count 1 --instance-type ${instanceType} --key-name cbdyncluster --security-group-ids sg-038fbc90bd62206af --region ${region} --output text --query 'Instances[*].InstanceId' --block-device-mappings 'DeviceName=/dev/sda1,Ebs={VolumeSize=20}'").trim()
                    echo "Created AWS instance ${instanceId}"

                    try {
                        runAWS("ec2 wait instance-running --region ${region} --instance-ids ${instanceId}")

                        String ip = runAWS("ec2 describe-instances --region ${region} --instance-ids ${instanceId} --output text --query 'Reservations[0].Instances[0].NetworkInterfaces[0].Association.PublicIp'").trim()

                        echo "AWS instance running on ${ip}"

                        // Setup Docker
                        runSSH(ip, "sudo yum update -y")
                        // Anytime we're returning output here, it's just to hide very verbose output
                        runSSH(ip, "sudo amazon-linux-extras install docker", true)
                        // Don't require docker to be run as sudo
                        runSSH(ip, "sudo usermod -aG docker ec2-user")
                        runSSH(ip, "sudo systemctl start docker")
                        // All Docker containers will be run on this network
                        runSSH(ip, "docker network create perf")

                        // We've install just the minimum to get the Cluster up, so it can be coming up while we're doing other stuff
                        runSSH(ip, "docker run -d --name cbs --network perf -p 8091-8096:8091-8096 -p 11210-11211:11210-11211 couchbase >/dev/null 2>&1", true)

                        runSSH(ip, "sudo yum install -y git java-17-amazon-corretto-devel", true)

                        runSSH(ip, "git clone https://github.com/couchbaselabs/perf-sdk.git")
                        runSSH(ip, "git clone https://github.com/couchbaselabs/jenkins-sdk")
                        runSSH(ip, "cd jenkins-sdk && ./gradlew -q shadowJar")
                        runSSH(ip, "find . -iname '*SNAPSHOT*.jar'")

                        // Cluster should be up by now
                        script {
                            // Have 32GB on these nodes, leave 4GB for the driver and performer
                            def memoryQuota = 28000
                            runSSH(ip, "docker exec cbs opt/couchbase/bin/couchbase-cli cluster-init -c localhost --cluster-username Administrator --cluster-password password --services data,index,query --cluster-ramsize ${memoryQuota}")
                            runSSH(ip, "docker exec cbs opt/couchbase/bin/couchbase-cli bucket-create -c localhost --username Administrator --password password --bucket default --bucket-type couchbase --bucket-ramsize ${memoryQuota}")
                            runSSH(ip, "curl -u Administrator:password http://localhost:8091/pools/default -d memoryQuota=${memoryQuota}")

                            // We're focussed on SDK performance, so disable server settings that can affect performance
                            runSSH(ip, "curl -u Administrator:password http://localhost:8091/controller/setAutoCompaction -d memoryQuota=${memoryQuota}")
                        }

                        // Run jenkins-sdk, which will do everything else
                        sh(script: 'ssh -o "StrictHostKeyChecking=no" -i $SSH_KEY_PATH ec2-user@' + ip + ' "cd jenkins-sdk && java -jar build/libs/jenkins2-1.0-SNAPSHOT-all.jar $TIMEDB_PWD"')
                    }
                    finally {
                        // For debugging, can log into the agent (if Mac) or the AWS instance now
                        // sleep(1000 * 60 * 60)
                        // echo "ssh couchbase@${agentIp}"
                        // echo "ssh -i ~/keys/cbdyncluster.pem ec2-user@${ip}"

                        runAWS("ec2 terminate-instances --instance-ids ${instanceId} --region ${region}")
                    }
                }
            }
        }
    }


}
