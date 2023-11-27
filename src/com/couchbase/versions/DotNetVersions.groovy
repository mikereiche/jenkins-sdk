package com.couchbase.versions

import com.couchbase.tools.network.NetworkUtil
import groovy.transform.Memoized


class DotNetVersions {
    private final static String REPO = "couchbase/couchbase-net-client"

    @Memoized
    static String getLatestSha() {
        return GithubVersions.getLatestSha(REPO)
    }

    @Memoized
    static Set<ImplementationVersion> getAllReleases() {
        return GithubVersions.getAllReleases(REPO)
    }
}
