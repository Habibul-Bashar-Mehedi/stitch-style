package com.stitchstyle.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * OpenAPI/Swagger UI configuration.
 *
 * <p>springdoc derives the document from the controllers; this class adds the
 * parts the generated document cannot infer:
 * <ul>
 *   <li>a stable, readable tag per business area (Storefront, Customer, Admin, ...)
 *       instead of the raw controller bean names,</li>
 *   <li>human-readable operation summaries from the Java method name,</li>
 *   <li>the two authentication schemes the browser actually uses (session cookie
 *       and the {@code X-XSRF-TOKEN} header), applied only to protected routes,</li>
 *   <li>an explicit CSRF header parameter on every state-changing operation,
 *       except the payment callbacks and dev helpers that are CSRF-exempt.</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    private static final String SESSION_COOKIE = "STITCHSTYLE_SESSION";
    private static final String XSRF_HEADER = "X-XSRF-TOKEN";
    private static final String SESSION_SCHEME = "sessionCookie";
    private static final String XSRF_SCHEME = "xsrfToken";

    /** Group order used for the tag list in the UI. */
    private static final Map<String, Integer> GROUP_ORDER = Map.of(
            "Storefront", 0,
            "Customer", 1,
            "Admin", 2,
            "Authentication", 3,
            "Developer", 4,
            "Other", 5);

    private static final Map<String, String> GROUP_DESCRIPTIONS = Map.of(
            "Storefront", "Public shop endpoints. No authentication required.",
            "Customer", "Signed-in customer endpoints. Requires a customer session.",
            "Admin", "Back-office endpoints. Requires a signed-in ADMIN or MANAGER session.",
            "Authentication", "Sign-in and sign-out lifecycle.",
            "Developer", "Developer-only helpers. Only enabled when app.dev-tools.enabled=true.",
            "Other", "Endpoints that do not belong to a business area.");

    private static final Set<String> CSRF_EXEMPT_PREFIXES = Set.of("/api/v1/dev");
    private static final Map<String, String> ACRONYMS = Map.of(
            "csrf", "CSRF",
            "seo", "SEO",
            "sku", "SKU",
            "api", "API");

    private final String serverPort;
    private final String configuredServerUrl;
    private final String publicBaseUrl;

    public OpenApiConfig(@Value("${server.port:38133}") String serverPort,
                         @Value("${app.openapi.server-url:}") String configuredServerUrl,
                         @Value("${app.public-base-url:}") String publicBaseUrl) {
        this.serverPort = serverPort;
        this.configuredServerUrl = configuredServerUrl;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Bean
    OpenAPI stitchStyleOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Stitch & Style API")
                        .version("v1")
                        .description("""
                                REST API for the Stitch & Style storefront, customer account and back office.

                                ### Authentication
                                Browser clients authenticate with the HTTP session cookie `STITCHSTYLE_SESSION`,
                                created by Google OIDC sign-in at `GET /oauth2/authorization/google`.

                                | Area | Access |
                                | --- | --- |
                                | Storefront | Public |
                                | Customer (`/api/v1/me/**`) | Signed-in customer session |
                                | Admin (`/api/v1/admin/**`) | Signed-in `ADMIN` or `MANAGER` session |

                                Use **Authorize** to record that a session is required; the cookie itself is
                                sent by the browser automatically, so sign in first in the same tab.

                                The documentation itself is restricted to an `ADMIN` session and can be
                                opened from the admin navigation bar.

                                ### CSRF
                                Every state-changing request must send the `X-XSRF-TOKEN` header. Fetch the
                                token once with `GET /api/v1/public/csrf`, then copy the value of the
                                `XSRF-TOKEN` cookie into the `X-XSRF-TOKEN` field that Swagger UI shows on
                                every POST, PUT, PATCH and DELETE operation. A missing or stale token
                                returns `403`.

                                ### Errors
                                Errors use RFC 9457 `application/problem+json` with a `code` and a `traceId`.
                                """))
                .servers(servers())
                .components(new Components()
                        .addSecuritySchemes(SESSION_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name(SESSION_COOKIE)
                                .description("""
                                        Server-side session created by OIDC sign-in. The browser sends this
                                        cookie automatically; no value needs to be entered here."""))
                        .addSecuritySchemes(XSRF_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name(XSRF_HEADER)
                                .description("""
                                        CSRF token from `GET /api/v1/public/csrf`. Required on POST, PUT,
                                        PATCH and DELETE requests.""")));
    }

    @Bean
    OpenApiCustomizer stitchStyleOperationsCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }

            Set<String> usedTags = new LinkedHashSet<>();
            Comparator<Descriptor> order = Comparator
                    .comparingInt((Descriptor d) -> GROUP_ORDER.getOrDefault(d.group(), GROUP_ORDER.get("Other")))
                    .thenComparing(Descriptor::resource);

            openApi.getPaths().forEach((path, pathItem) -> {
                Descriptor descriptor = describe(path);
                usedTags.add(descriptor.tag());
                pathItem.readOperationsMap().forEach((method, operation) ->
                        polish(operation, method, path, descriptor));
            });

            List<Tag> tags = usedTags.stream()
                    .map(Descriptor::fromTag)
                    .sorted(order)
                    .map(d -> new Tag()
                            .name(d.tag())
                            .description(GROUP_DESCRIPTIONS.get(d.group())))
                    .toList();
            openApi.setTags(tags);
        };
    }

    private List<Server> servers() {
        List<Server> servers = new ArrayList<>();
        servers.add(new Server().url("/").description("This server (current origin)"));

        String direct = configuredServerUrl == null || configuredServerUrl.isBlank()
                ? "http://localhost:" + serverPort
                : configuredServerUrl.trim();
        if (!direct.isBlank()) {
            servers.add(new Server().url(trimTrailingSlash(direct)).description("Backend"));
        }
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()
                && !trimTrailingSlash(publicBaseUrl).equals(trimTrailingSlash(direct))) {
            servers.add(new Server().url(trimTrailingSlash(publicBaseUrl))
                    .description("Storefront / SSR origin"));
        }
        return servers;
    }

    private static void polish(Operation operation,
                               PathItem.HttpMethod method,
                               String path,
                               Descriptor descriptor) {
        operation.setTags(new ArrayList<>(List.of(descriptor.tag())));

        if (operation.getSummary() == null || operation.getSummary().isBlank()) {
            String summary = humanize(operation.getOperationId());
            if (summary != null) {
                operation.setSummary(summary);
            }
        }

        if (requiresAuthentication(path)) {
            SecurityRequirement requirement = new SecurityRequirement().addList(SESSION_SCHEME);
            if (isStateChanging(method)) {
                requirement.addList(XSRF_SCHEME);
            }
            operation.setSecurity(new ArrayList<>(List.of(requirement)));
        }

        if (isStateChanging(method) && requiresCsrfToken(path)) {
            addCsrfParameter(operation);
        }
    }

    private static void addCsrfParameter(Operation operation) {
        List<Parameter> parameters = operation.getParameters() == null
                ? new ArrayList<>()
                : new ArrayList<>(operation.getParameters());
        boolean present = parameters.stream()
                .anyMatch(p -> XSRF_HEADER.equalsIgnoreCase(p.getName()));
        if (!present) {
            parameters.add(new Parameter()
                    .in("header")
                    .name(XSRF_HEADER)
                    .required(false)
                    .description("Value of the `XSRF-TOKEN` cookie returned by `GET /api/v1/public/csrf`. Required.")
                    .schema(new StringSchema()));
            operation.setParameters(parameters);
        }
    }

    private static boolean requiresAuthentication(String path) {
        return path.equals("/api/v1/me")
                || path.startsWith("/api/v1/me/")
                || path.equals("/api/v1/admin")
                || path.startsWith("/api/v1/admin/")
                || path.equals("/api/v1/auth/logout");
    }

    private static boolean isStateChanging(PathItem.HttpMethod method) {
        return method != PathItem.HttpMethod.GET
                && method != PathItem.HttpMethod.HEAD
                && method != PathItem.HttpMethod.OPTIONS
                && method != PathItem.HttpMethod.TRACE;
    }

    private static boolean requiresCsrfToken(String path) {
        if (path.contains("/payments/") && path.endsWith("/callback")) {
            return false;
        }
        return CSRF_EXEMPT_PREFIXES.stream().noneMatch(path::startsWith);
    }

    private static Descriptor describe(String path) {
        if (path.equals("/sitemap.xml") || path.equals("/robots.txt") || path.startsWith("/media/")) {
            return new Descriptor("Storefront", "SEO");
        }
        if (path.startsWith("/api/v1/admin")) {
            return new Descriptor("Admin", resource(path, "/api/v1/admin", "Admin"));
        }
        if (path.equals("/api/v1/me") || path.startsWith("/api/v1/me/")) {
            return new Descriptor("Customer", resource(path, "/api/v1/me", "Account"));
        }
        if (path.startsWith("/api/v1/public")) {
            return new Descriptor("Storefront", resource(path, "/api/v1/public", "Storefront"));
        }
        if (path.startsWith("/api/v1/auth") || path.startsWith("/oauth2") || path.startsWith("/login/oauth2")) {
            return new Descriptor("Authentication", "Authentication");
        }
        if (path.startsWith("/api/v1/dev")) {
            return new Descriptor("Developer", resource(path, "/api/v1/dev", "Developer tools"));
        }
        return new Descriptor("Other", "Miscellaneous");
    }

    /** First path segment after the prefix, e.g. {@code /api/v1/admin/delivery-areas} -> "Delivery areas". */
    private static String resource(String path, String prefix, String fallback) {
        String rest = path.substring(prefix.length());
        for (String segment : rest.split("/")) {
            if (!segment.isBlank() && !segment.startsWith("{")) {
                String label = segment.replace('-', ' ').replace('_', ' ').trim();
                String acronym = ACRONYMS.get(label.toLowerCase(Locale.ROOT));
                if (acronym != null) {
                    return acronym;
                }
                return Character.toUpperCase(label.charAt(0)) + label.substring(1);
            }
        }
        return fallback;
    }

    private static String humanize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String spaced = raw.replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()
                .toLowerCase(Locale.ROOT);
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private record Descriptor(String group, String resource) {

        String tag() {
            return group + " · " + resource;
        }

        static Descriptor fromTag(String tag) {
            int separator = tag.indexOf(" · ");
            return new Descriptor(tag.substring(0, separator), tag.substring(separator + 3));
        }
    }
}
