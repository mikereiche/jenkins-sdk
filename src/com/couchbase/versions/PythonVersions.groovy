package com.couchbase.versions

import com.couchbase.tools.network.NetworkUtil
import groovy.transform.Memoized


class PythonVersions {
    @Memoized
    static String getLatestSha() {
        def json = NetworkUtil.readJson("https://api.github.com/repos/couchbase/couchbase-python-client/commits/master")
        String sha = json.sha
        return sha.substring(0, 7)
    }

    @Memoized
    static Set<ImplementationVersion> getAllReleases() {
        def out = new HashSet<ImplementationVersion>()
        def json = NetworkUtil.readJson("https://api.github.com/repos/couchbase/couchbase-python-client/tags")

        for (doc in json) {
            String version = doc.name
            try {
                out.add(ImplementationVersion.from(version))
            }
            catch (err) {
                System.err.println("Failed to add python version ${doc}")
            }
        }

        return out
    }
}
