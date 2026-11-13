package com.stitchstyle.gatling.simulations;

import com.stitchstyle.gatling.Catalogue;
import com.stitchstyle.gatling.Config;
import com.stitchstyle.gatling.Journeys;
import io.gatling.javaapi.core.ScenarioBuilder;

import static io.gatling.javaapi.core.CoreDsl.scenario;

/**
 * Persistent customer cart: PUT/GET/DELETE /me/cart. Requires dev-tools.
 *
 *   MODE=customer mvn gatling:test -Dgatling.simulationClass=...CartSimulation
 */
public class CartSimulation extends AbstractLoadSimulation {

    public CartSimulation() {
        if (!"customer".equals(Config.mode())) {
            throw new IllegalStateException(
                    "CartSimulation is the persistent-cart profile. Run it with MODE=customer.");
        }

        ScenarioBuilder scenario = scenario("Persistent cart")
                .feed(Catalogue.feeder())
                .exec(Journeys.devLoginPerUser("loadtest-cart", "CUSTOMER"))
                .exec(Journeys.csrf())
                .exec(Journeys.replaceCart())
                .exec(Journeys.getCart())
                .exec(Journeys.removeCartLine())
                .exec(Journeys.cartPrice())
                .pause(Config.think());

        setUp(closedLoad(scenario));
    }

    @Override
    public void before() {
        int code = Catalogue.probeDevLogin("loadtest-cart-probe@example.com", "CUSTOMER");
        if (code != 200) {
            throw new IllegalStateException("CartSimulation requires DEV_TOOLS_ENABLED=true on the backend "
                    + "(dev-session probe returned " + code + ").");
        }
    }
}
