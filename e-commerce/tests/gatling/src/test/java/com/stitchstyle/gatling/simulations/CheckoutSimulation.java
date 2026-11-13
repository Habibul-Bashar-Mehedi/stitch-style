package com.stitchstyle.gatling.simulations;

import com.stitchstyle.gatling.Catalogue;
import com.stitchstyle.gatling.Config;
import com.stitchstyle.gatling.Journeys;
import io.gatling.javaapi.core.ScenarioBuilder;

import static io.gatling.javaapi.core.CoreDsl.scenario;

/**
 * COD checkout: price, place an order, track it. WRITES real orders and stock.
 *
 *   mvn gatling:test -Dgatling.simulationClass=com.stitchstyle.gatling.simulations.CheckoutSimulation
 */
public class CheckoutSimulation extends AbstractLoadSimulation {

    public CheckoutSimulation() {
        ScenarioBuilder scenario = scenario("Checkout (COD)")
                .feed(Catalogue.feeder())
                .exec(Journeys.csrf())
                .exec(Journeys.cartPrice())
                .exec(Journeys.placeOrder("COD", "gatling load test"))
                .exec(Journeys.trackOrder())
                .pause(Config.think());

        setUp(closedLoad(scenario));
    }
}
