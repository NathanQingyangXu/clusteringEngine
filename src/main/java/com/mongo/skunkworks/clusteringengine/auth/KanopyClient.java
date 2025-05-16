package com.mongo.skunkworks.clusteringengine.auth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public final class KanopyClient {
    private static final String KANOPY_OIDC_COMMAND_PATH = "/Users/nathan/kanopy-oidc/bin/kanopy-oidc";
    private static final String KANOPY_OIDC_COMMAND_ARGUMENTS = "login";

    private KanopyClient() {}

    public static String getToken() throws IOException {
        var process = Runtime.getRuntime().exec(KANOPY_OIDC_COMMAND_PATH + " " + KANOPY_OIDC_COMMAND_ARGUMENTS);
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            return reader.readLine();
        }
    }

    public static void main(String[] args) throws Exception {
        var token = getToken();
        System.out.println(token);
    }
}
