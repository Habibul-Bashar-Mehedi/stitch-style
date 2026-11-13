package com.stitchstyle.gatling.simulations;

import com.stitchstyle.gatling.Catalogue;
import com.stitchstyle.gatling.Config;
import com.stitchstyle.gatling.Journeys;
import io.gatling.javaapi.core.ScenarioBuilder;

import static io.gatling.javaapi.core.CoreDsl.scenario;

/**
 * Admin dashboard: analytics summary plus the order/product/notice lists.
 * Requires dev-tools (Google OIDC cannot be automated).
 *
 *   ADMIN_EMAIL=loadtest-admin@example.com mvn gatling:test -Dgatling.simulationClass=...DashboardSimulation
 */
public class DashboardSimulation extends AbstractLoadSimulation {

    public DashboardSimulation() {
        ScenarioBuilder scenario = scenario("Admin dashboard")
                .exec(Journeys.devLogin(Config.adminEmail(), "ADMIN", "Load test admin"))
                .exec(Journeys.csrf())
                .exec(Journeys.analytics())
                .exec(Journeys.adminOrders())
                .exec(Journeys.adminProducts())
                .exec(Journeys.adminNotices())
                .pause(Config.think());

        setUp(closedLoad(scenario));
    }

    @Override
    public void before() {
        int code = Catalogue.probeDevLogin(Config.adminEmail(), "ADMIN");
        if (code != 200) {
            throw new IllegalStateException("DashboardSimulation requires DEV_TOOLS_ENABLED=true on the "
                    + "backend (dev-session probe returned " + code + ").");
        }
    }
}
