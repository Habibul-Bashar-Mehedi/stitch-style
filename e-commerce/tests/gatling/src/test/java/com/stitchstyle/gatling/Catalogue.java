package com.stitchstyle.gatling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gatling.javaapi.core.CoreDsl;
import io.gatling.javaapi.core.FeederBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the delivery area and every in-stock variant once, before the users are
 * injected. The data is exposed as a circular Gatling feeder so each request
 * order/cart line uses a real, currently-available variant.
 */
public final class Catalogue {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final long AREA_ID;
    private static final List<Long> VARIANT_IDS;
    private static final List<Map<String, Object>> ROWS;

    static {
        try {
            JsonNode areas = MAPPER.readTree(get("/api/v1/public/delivery-areas"));
            if (!areas.isArray() || areas.isEmpty()) {
                throw new IllegalStateException("No delivery areas: seed the database (scripts/demo-data.sql).");
            }
            AREA_ID = areas.get(0).path("id").asLong();

            JsonNode products = MAPPER.readTree(get("/api/v1/public/products?size=100"));
            List<Long> ids = new ArrayList<>();
            for (JsonNode product : products.path("content")) {
                for (JsonNode variant : product.path("variants")) {
                    if (variant.path("active").asBoolean() && variant.path("available").asInt() > 0) {
                        ids.add(variant.path("id").asLong());
                    }
                }
            }
            if (ids.isEmpty()) {
                throw new IllegalStateException(
                        "No in-stock variants: restock before running a checkout profile (see README).");
            }
            VARIANT_IDS = List.copyOf(ids);

            List<Map<String, Object>> rows = new ArrayList<>();
            for (Long id : ids) {
                Map<String, Object> row = new HashMap<>();
                row.put("variantId", id);
                row.put("areaId", AREA_ID);
                rows.add(row);
            }
            ROWS = List.copyOf(rows);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private Catalogue() {
    }

    /** A fresh circular feeder per scenario (feeders are stateful). */
    public static FeederBuilder<Object> feeder() {
        return CoreDsl.listFeeder(ROWS).circular();
    }

    public static int variantCount() {
        return VARIANT_IDS.size();
    }

    public static long areaId() {
        return AREA_ID;
    }

    /** Probes the dev-tools sign-in endpoint; used to fail fast when it is disabled. */
    public static int probeDevLogin(String email, String role) {
        try {
            String body = String.format("{\"email\":\"%s\",\"role\":\"%s\"}", email, role);
            HttpRequest request = HttpRequest.newBuilder(URI.create(Config.baseUrl() + "/api/v1/dev/session"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            return CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
        } catch (Exception e) {
            return -1;
        }
    }

    private static String get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(Config.baseUrl() + path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("GET " + path + " returned " + response.statusCode());
        }
        return response.body();
    }
}
