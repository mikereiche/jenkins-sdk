package com.couchbase.versions

import com.couchbase.tools.network.NetworkUtil
import groovy.transform.Memoized


class GoVersions {
    @Memoized
    static ImplementationVersion getLatestGoModEntry() {
        def json = NetworkUtil.readJson("https://api.github.com/repos/couchbase/gocb/commits/master")

        def tagsJson = NetworkUtil.readJson("https://api.github.com/repos/couchbase/gocb/tags")
        def latestTagSha = tagsJson[0].commit.sha

        String sha = json.sha
        if (sha == latestTagSha) {
            return null
        }

        String commitDate = json.commit.committer.date
        String goModSha = commitDate.replaceAll("[^0-9]", "") + "-" + sha.substring(0, 12)

        def allReleases = GoVersions.getAllReleases()
        def highest = ImplementationVersion.highest(allReleases)
        def patch = highest.patch + 1
        def out = ImplementationVersion.from("${highest.major}.${highest.minor}.${patch}-${goModSha}")
        return out
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
