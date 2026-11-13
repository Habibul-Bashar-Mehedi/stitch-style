package com.stitchstyle.gatling.simulations;

import com.stitchstyle.gatling.Catalogue;
import com.stitchstyle.gatling.Config;
import com.stitchstyle.gatling.Journeys;
import io.gatling.javaapi.core.ScenarioBuilder;

import static io.gatling.javaapi.core.CoreDsl.scenario;

/**
 * Read-only storefront: home, product list and cart pricing at VUS concurrency.
 *
 *   mvn gatling:test -Dgatling.simulationClass=com.stitchstyle.gatling.simulations.CatalogueSimulation
 */
public class CatalogueSimulation extends AbstractLoadSimulation {

    public CatalogueSimulation() {
        ScenarioBuilder scenario = scenario("Catalogue")
                .feed(Catalogue.feeder())
                .exec(Journeys.csrf())
                .exec(Journeys.home())
                .exec(Journeys.products())
                .exec(Journeys.cartPrice())
                .pause(Config.think());

        setUp(closedLoad(scenario));
    }
}
