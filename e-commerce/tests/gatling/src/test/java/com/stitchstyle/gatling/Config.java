package com.stitchstyle.gatling;

/**
 * Every knob is read from an environment variable first, then a JVM system
 * property, then a default. That way both `VUS=500 mvn gatling:test ...` and
 * `mvn gatling:test -Dvus=500 ...` work.
 */
public final class Config {

    private Config() {
    }

    public static String baseUrl() {
        return value("BASE_URL", "baseUrl", "http://localhost:38133");
    }

    public static int vus() {
        return intValue("VUS", "vus", 500);
    }

    public static int ramp() {
        return intValue("RAMP", "ramp", 20);
    }

    public static int hold() {
        return intValue("HOLD", "hold", 20);
    }

    public static int think() {
        return intValue("THINK", "think", 1);
    }

    public static String mode() {
        return value("MODE", "mode", "guest").toLowerCase();
    }

    public static String paymentProvider() {
        return value("PAYMENT_PROVIDER", "paymentProvider", "BKASH").toUpperCase();
    }

    public static String callbackSecret() {
        return value("CALLBACK_SECRET", "callbackSecret", "dev-callback-secret");
    }

    public static String adminEmail() {
        return value("ADMIN_EMAIL", "adminEmail", "loadtest-admin@example.com");
    }

    public static String levels() {
        return value("LEVELS", "levels", "500,1000,2000");
    }

    private static String value(String env, String property, String fallback) {
        String fromEnv = System.getenv(env);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String fromProperty = System.getProperty(property);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty.trim();
        }
        return fallback;
    }

    private static int intValue(String env, String property, int fallback) {
        return Integer.parseInt(value(env, property, String.valueOf(fallback)));
    }
}
