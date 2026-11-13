package com.stitchstyle.gatling;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.Session;

import java.util.UUID;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.http.HttpDsl.CookieKey;
import static io.gatling.javaapi.http.HttpDsl.getCookieValue;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/** Reusable storefront / admin request chains. */
public final class Journeys {

    private Journeys() {
    }

    /** GET /csrf and read the (possibly pre-existing) XSRF-TOKEN cookie out of the jar. */
    public static ChainBuilder csrf() {
        return exec(
                http("csrf").get("/api/v1/public/csrf"),
                getCookieValue(CookieKey("XSRF-TOKEN").saveAs("xsrf")));
    }

    public static ChainBuilder devLogin(String email, String role, String fullName) {
        String body = String.format(
                "{\"email\":\"%s\",\"role\":\"%s\",\"fullName\":\"%s\"}", email, role, fullName);
        return exec(http("dev-login")
                .post("/api/v1/dev/session")
                .body(StringBody(body))
                .asJson()
                .check(status().is(200)));
    }

    /** One account per virtual user, e.g. loadtest-cart-12@example.com. */
    public static ChainBuilder devLoginPerUser(String prefix, String role) {
        return exec(http("dev-login")
                .post("/api/v1/dev/session")
                .body(StringBody(s -> String.format(
                        "{\"email\":\"%s-%d@example.com\",\"role\":\"%s\",\"fullName\":\"Gatling %d\"}",
                        prefix, s.userId(), role, s.userId())))
                .asJson()
                .check(status().is(200)));
    }

    public static ChainBuilder home() {
        return exec(http("home").get("/api/v1/public/home").check(status().is(200)));
    }

    public static ChainBuilder products() {
        return exec(http("products")
                .get("/api/v1/public/products?size=12")
                .check(status().is(200)));
    }

    public static ChainBuilder cartPrice() {
        return exec(http("cart-price")
                .post("/api/v1/public/cart/price")
                .header("X-XSRF-TOKEN", "#{xsrf}")
                .body(StringBody(s -> String.format(
                        "{\"items\":[{\"variantId\":%d,\"quantity\":1,\"selected\":true}]}",
                        s.getLong("variantId"))))
                .asJson()
                .check(status().is(200)));
    }

    public static ChainBuilder replaceCart() {
        return exec(http("cart-replace")
                .put("/api/v1/me/cart")
                .header("X-XSRF-TOKEN", "#{xsrf}")
                .body(StringBody(s -> String.format(
                        "{\"items\":[{\"variantId\":%d,\"quantity\":2,\"selected\":true}]}",
                        s.getLong("variantId"))))
                .asJson()
                .check(status().is(200)));
    }

    public static ChainBuilder getCart() {
        return exec(http("cart-get").get("/api/v1/me/cart").check(status().is(200)));
    }

    public static ChainBuilder removeCartLine() {
        return exec(http("cart-remove")
                .delete(s -> "/api/v1/me/cart/" + s.getLong("variantId"))
                .header("X-XSRF-TOKEN", "#{xsrf}")
                .check(status().is(200)));
    }

    /**
     * Places one order. Saves publicToken (and redirectUrl + grandTotal when the
     * order is prepaid) for the follow-up tracking/payment steps.
     */
    public static ChainBuilder placeOrder(String paymentMethod, String note) {
        boolean prepaid = !"COD".equals(paymentMethod);
        var request = http("place-order")
                .post("/api/v1/public/orders")
                .header("Idempotency-Key", s -> "gt-" + UUID.randomUUID())
                .header("Forwarded", Journeys::forwarded)
                .header("X-XSRF-TOKEN", "#{xsrf}")
                .body(StringBody(s -> String.format(
                        "{\"customer\":{\"name\":\"Gatling %d\",\"phone\":\"%s\"},"
                                + "\"shippingAddress\":\"House %d, Road 1, Dhaka\","
                                + "\"deliveryAreaId\":%d,\"paymentMethod\":\"%s\",\"note\":\"%s\","
                                + "\"items\":[{\"variantId\":%d,\"quantity\":1,\"selected\":true}]}",
                        s.userId(), phone(s), s.userId(), s.getLong("areaId"),
                        paymentMethod, note, s.getLong("variantId"))))
                .asJson()
                .check(status().in(200, 201))
                .check(jsonPath("$.publicToken").saveAs("publicToken"));

        if (prepaid) {
            request = request
                    .check(jsonPath("$.redirectUrl").saveAs("redirectUrl"))
                    .check(jsonPath("$.grandTotal").saveAs("grandTotal"));
        }
        return exec(request);
    }

    public static ChainBuilder trackOrder() {
        return exec(http("track-order")
                .get("/api/v1/public/orders/track/#{publicToken}")
                .check(status().is(200)));
    }

    public static ChainBuilder paymentCallback() {
        return exec(http("payment-callback")
                .post("/api/v1/public/payments/" + Config.paymentProvider() + "/callback")
                .queryParam("signature", Config.callbackSecret())
                .queryParam("status", "success")
                .queryParam("providerTxnId", s -> txnFrom(s.getString("redirectUrl")))
                .queryParam("amount", "#{grandTotal}")
                .queryParam("currency", "BDT")
                .check(status().is(200)));
    }

    public static ChainBuilder trackPaidOrder() {
        return exec(http("track-order")
                .get("/api/v1/public/orders/track/#{publicToken}")
                .check(status().is(200))
                .check(jsonPath("$.paymentStatus").is("PAID")));
    }

    public static ChainBuilder analytics() {
        return exec(http("analytics-summary")
                .get("/api/v1/admin/analytics/summary")
                .check(status().is(200)));
    }

    public static ChainBuilder adminOrders() {
        return exec(http("admin-orders")
                .get("/api/v1/admin/orders?size=20")
                .check(status().is(200)));
    }

    public static ChainBuilder adminProducts() {
        return exec(http("admin-products")
                .get("/api/v1/admin/products?size=20")
                .check(status().is(200)));
    }

    public static ChainBuilder adminNotices() {
        return exec(http("admin-notices").get("/api/v1/admin/notices").check(status().is(200)));
    }

    static String phone(Session session) {
        return String.format("017%08d", session.userId() % 100_000_000L);
    }

    /**
     * A different emulated client IP per order, so the backend's 5/min per-IP
     * order limit does not turn a load test into a 429 test. Pair with
     * SPOOF=false to load-test the limiter itself.
     */
    static String forwarded(Session session) {
        if (!Boolean.parseBoolean(System.getenv().getOrDefault("SPOOF", "true"))) {
            return "for=127.0.0.1";
        }
        long user = session.userId();
        int last = Math.floorMod(UUID.randomUUID().hashCode(), 250) + 1;
        return "for=10." + (user / 250) + "." + (user % 250) + "." + last;
    }

    private static String txnFrom(String redirectUrl) {
        if (redirectUrl == null) {
            return "";
        }
        var matcher = java.util.regex.Pattern.compile("[?&]txn=([^&]+)").matcher(redirectUrl);
        return matcher.find() ? matcher.group(1) : "";
    }
}
