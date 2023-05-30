package com.couchbase.versions

import com.couchbase.tools.network.NetworkUtil
import groovy.transform.Memoized


class CppVersions {
    @Memoized
    static String getLatestSha() {
        def json = NetworkUtil.readJson("https://api.github.com/repos/couchbaselabs/couchbase-cxx-client/commits/main")
        String sha = json.sha
        String commitDate = json.commit.committer.date
        String[] parts = commitDate.split("T")
        String date = parts[0].replaceAll("[^0-9]", "")
        String time = parts[1].replaceAll("[^0-9]", "")
        return date + "." + time + "-" + sha.substring(0, 7)
    }

    @Memoized
    static Set<ImplementationVersion> getAllReleases() {
        def out = new HashSet<ImplementationVersion>()
        def json = NetworkUtil.readJson("https://api.github.com/repos/couchbaselabs/couchbase-cxx-client/tags")

        for (doc in json) {
            String version = doc.name
            try {
                out.add(ImplementationVersion.from(version))
            }
            catch (err) {
                System.err.println("Failed to add C++ version ${doc}")
            }
        }

        return withoutUnsupportedVersions(out)
    }

    /**
     * Removes the versions that are not supported by the C++ Performer. Currently the unsupported versions are
     * all 1.0.0-beta.X releases and 1.0.0-dp.X releases where X < 4
     */
    private static Set<ImplementationVersion> withoutUnsupportedVersions(Set<ImplementationVersion> allVersions) {
        return allVersions.findAll( (v) -> {
            if (!(v.major == 1 && v.minor == 0 && v.patch == 0)) return true

            String[] parts = v.snapshot.substring(1).split("\\.")
            if (parts[0] == "dp") {
                return Integer.parseInt(parts[1]) >= 4
            } else return parts[0] != "beta"
        })
    }
}
