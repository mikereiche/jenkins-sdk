package com.couchbase.versions

import com.couchbase.tools.network.NetworkUtil
import groovy.transform.Memoized


class GoVersions {
    @Memoized
    static String getLatestGoModEntry() {
        def json = NetworkUtil.readJson("https://proxy.golang.org/github.com/couchbase/gocb/v2/@v/master.info")

        def latestVersion = json.Version
        return latestVersion.substring(1, latestVersion.length())
    }

    @Memoized
    static Set<ImplementationVersion> getAllReleases() {
        def out = new HashSet<ImplementationVersion>()
        def json = NetworkUtil.readJson("https://api.github.com/repos/couchbase/gocb/tags")

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
