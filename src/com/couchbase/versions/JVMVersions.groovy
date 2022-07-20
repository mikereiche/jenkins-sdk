package com.couchbase.versions

import groovy.json.JsonSlurper
import groovy.transform.Memoized


class JVMVersions {
    public static String read(String url) {
        def get = new URL(url).openConnection();
        return get.getInputStream().getText()
    }

    @Memoized
    static ImplementationVersion getLatestSnapshotBuild(String client) {
        String snapshotsContent = read("https://oss.sonatype.org/content/repositories/snapshots/com/couchbase/client/${client}/maven-metadata.xml")
        def snapshots = new groovy.xml.XmlSlurper().parseText(snapshotsContent)

        // "latest" doesn't look up to date so assuming list will always be time-ordered
        def lastSnapshot = snapshots.versioning.versions.childNodes()[snapshots.versioning.versions.childNodes().size() - 1].text()

        String content = read("https://oss.sonatype.org/content/repositories/snapshots/com/couchbase/client/${client}/${lastSnapshot}/maven-metadata.xml")
        def xml = new groovy.xml.XmlSlurper().parseText(content)
        // "20220715.074746-6"
        def timestamp = xml.versioning.snapshot.timestamp
        def builderNumber = xml.versioning.snapshot.buildNumber
        def version = ImplementationVersion.from(lastSnapshot)
        def out = ImplementationVersion.from("${version.major}.${version.minor}.${version.patch}-${timestamp}-${builderNumber}")
        return out
    }

    @Memoized
    static Set<ImplementationVersion> getAllJVMReleases(String client) {
        int start = 0
        def out = new HashSet<ImplementationVersion>()

        while (true) {
            String url = "https://search.maven.org/solrsearch/select?q=g:com.couchbase.client+AND+a:${client}&start=${start}&core=gav&rows=20&wt=json"
            def get = new URL(url).openConnection();
            String content = get.getInputStream().getText()
            def parser = new JsonSlurper()
            def json = parser.parseText(content)
            def docs = json.response.docs

            if (docs.size() == 0) {
                break
            }
            else {
                for (doc in docs) {
                    String version = doc.v
                    try {
                        out.add(ImplementationVersion.from(version))
                    }
                    catch (err) {
                        System.err.println("Failed to add version ${client} ${doc}")
                    }
                }
                start += docs.size()
            }
        }

        return out
    }
}
