package com.couchbase.versions

import com.couchbase.tools.network.NetworkUtil
import groovy.transform.Memoized


class PythonVersions {
    private final static String REPO = "couchbase/couchbase-python-client"
    private final static String BRANCH = "main"

    @Memoized
    static String getLatestSha() {
        return GithubVersions.getLatestShaWithDatetime(REPO, BRANCH)
    }

    @Memoized
    static Set<ImplementationVersion> getAllReleases() {
        return GithubVersions.getAllReleases(REPO)
    }
}
