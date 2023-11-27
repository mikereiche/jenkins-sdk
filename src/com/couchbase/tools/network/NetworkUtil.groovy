package com.couchbase.tools.network

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult


class NetworkUtil {
    @CompileStatic
    public static String read(String url, int attempts = 5) {
        return readRaw(url, attempts).getInputStream().getText()
    }

    @CompileStatic
    public static URLConnection readRaw(String url, int attempts = 5) {
        while (true) {
            try {
                return new URL(url).openConnection();
            }
            catch (err) {
                attempts -= 1
                if (attempts <= 0) {
                    throw err
                }
                else {
                    System.err.println("Retrying on ${url} on error ${err} on attempt ${attempts}")
                }
            }
        }
    }

    @CompileStatic
    public static Object readJson(String url, int attempts = 5) {
        String content = read(url, attempts)
        def parser = new JsonSlurper()
        return parser.parseText(content)
    }

    @CompileStatic
    public static GPathResult readXml(String url, int attempts = 5) {
        String content = read(url, attempts)
        def parser = new XmlSlurper()
        return parser.parseText(content)
    }
}
