package com.couchbase.versions

import groovy.json.JsonSlurper
import groovy.transform.Memoized


class JVMVersions {
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
