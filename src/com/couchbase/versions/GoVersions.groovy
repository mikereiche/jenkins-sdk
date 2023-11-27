package com.couchbase.versions

import com.couchbase.tools.network.NetworkUtil
import groovy.transform.Memoized


class GoVersions {
    private final static String REPO = "couchbase/gocb"

    @Memoized
    static String getLatestGoModEntry() {
        def json = NetworkUtil.readJson("https://proxy.golang.org/github.com/couchbase/gocb/v2/@v/master.info")

        def latestVersion = json.Version
        return latestVersion.substring(1, latestVersion.length())
    }

    @Memoized
    static Set<ImplementationVersion> getAllReleases() {
        return GithubVersions.getAllReleases(REPO)
    }
}
