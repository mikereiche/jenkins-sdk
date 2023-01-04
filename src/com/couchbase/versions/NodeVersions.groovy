package com.couchbase.versions

import com.couchbase.tools.network.NetworkUtil
import groovy.json.JsonSlurper
import groovy.transform.Memoized

class NodeVersions {
    @Memoized
    static String getLatestSha() {
        def json = NetworkUtil.readJson("https://api.github.com/repos/couchbase/couchnode/commits/master")
        String sha = json.sha
        return sha.substring(0, 7)
    }

    @Memoized
    static Set<ImplementationVersion> getAllReleases() {
        def out = new HashSet<ImplementationVersion>()

        String url = "https://api.github.com/repos/couchbase/couchnode/tags"
        String content = NetworkUtil.read(url)
        def parser = new JsonSlurper()
        def json = parser.parseText(content)

        for (doc in json) {
            String version = doc.name
            try {
                out.add(ImplementationVersion.from(version.substring(1, version.length())))
            }
            catch (err) {
                System.err.println("Failed to add node version ${doc}")
            }
        }

        return out
    }
}
