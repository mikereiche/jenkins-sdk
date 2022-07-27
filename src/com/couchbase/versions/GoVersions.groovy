package com.couchbase.versions

import groovy.json.JsonSlurper
import groovy.transform.Memoized


class GoVersions {
    @Memoized
    static String getLatestSha() {
        String content = JVMVersions.read("https://api.github.com/repos/couchbase/gocb/commits/master")
        def parser = new JsonSlurper()
        def json = parser.parseText(content)
        String sha = json.sha
        String commitDate = json.commit.committer.date


        return commitDate.replaceAll("[^0-9]", "") + "-" + sha.substring(0, 12)
    }

    @Memoized
    static Set<ImplementationVersion> getAllReleases() {
        def out = new HashSet<ImplementationVersion>()

        String url = "https://api.github.com/repos/couchbase/gocb/tags"
        String content = JVMVersions.read(url)
        def parser = new JsonSlurper()
        def json = parser.parseText(content)

        for (doc in json) {
            String version = doc.name
            try {
                out.add(ImplementationVersion.from(version.substring(1, version.length())))
            }
            catch (err) {
                System.err.println("Failed to add go version ${doc}")
            }
        }

        return out
    }
}
