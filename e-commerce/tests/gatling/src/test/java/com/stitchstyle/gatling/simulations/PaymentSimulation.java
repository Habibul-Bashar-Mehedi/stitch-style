package com.stitchstyle.gatling.simulations;

import com.stitchstyle.gatling.Catalogue;
import com.stitchstyle.gatling.Config;
import com.stitchstyle.gatling.Journeys;
import io.gatling.javaapi.core.ScenarioBuilder;

import static io.gatling.javaapi.core.CoreDsl.scenario;

/**
 * Prepaid checkout end to end: order -> stub gateway -> callback -> PAID.
 * WRITES real orders/payments and commits stock.
 *
 *   PAYMENT_PROVIDER=BKASH mvn gatling:test -Dgatling.simulationClass=...PaymentSimulation
 */
public class PaymentSimulation extends AbstractLoadSimulation {

    public PaymentSimulation() {
        ScenarioBuilder scenario = scenario("Payment")
                .feed(Catalogue.feeder())
                .exec(Journeys.csrf())
                .exec(Journeys.placeOrder(Config.paymentProvider(), "gatling payment test"))
                .exec(Journeys.paymentCallback())
                .exec(Journeys.trackPaidOrder())
                .pause(Config.think());

        setUp(closedLoad(scenario));
    }
}
