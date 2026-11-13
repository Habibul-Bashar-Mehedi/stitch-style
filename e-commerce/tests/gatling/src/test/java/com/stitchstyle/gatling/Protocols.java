package com.stitchstyle.gatling;

import io.gatling.javaapi.http.HttpDsl;
import io.gatling.javaapi.http.HttpProtocolBuilder;

public final class Protocols {

    private Protocols() {
    }

    public static HttpProtocolBuilder http() {
        return HttpDsl.http
                .baseUrl(Config.baseUrl())
                .acceptHeader("application/json")
                .disableCaching()
                .shareConnections();
    }
}
