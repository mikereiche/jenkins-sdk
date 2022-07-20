package com.couchbase.versions

import groovy.json.JsonSlurper
import groovy.transform.Memoized


class DotNetVersions {
    @Memoized
    static String getLatestSha() {
        String content = JVMVersions.read("https://api.github.com/repos/couchbase/couchbase-net-client/commits/master")
        def parser = new JsonSlurper()
        def json = parser.parseText(content)
        String sha = json.sha

        return sha.substring(0, 6)
    }

    @Memoized
    static Set<ImplementationVersion> getAllReleases() {
        def out = new HashSet<ImplementationVersion>()

        String url = "https://api.github.com/repos/couchbase/couchbase-net-client/tags"
        String content = JVMVersions.read(url)
        def parser = new JsonSlurper()
        def json = parser.parseText(content)

        for (doc in json) {
            String version = doc.name
            try {
                out.add(ImplementationVersion.from(version))
            }
            catch (err) {
                System.err.println("Failed to add dotnet version ${doc}")
            }
        }

        return out
    }
}
