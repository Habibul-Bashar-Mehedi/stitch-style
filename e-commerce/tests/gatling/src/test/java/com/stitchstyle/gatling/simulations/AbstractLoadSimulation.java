package com.stitchstyle.gatling.simulations;

import com.stitchstyle.gatling.Config;
import com.stitchstyle.gatling.Protocols;
import io.gatling.javaapi.core.PopulationBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;

import static io.gatling.javaapi.core.CoreDsl.constantConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.rampConcurrentUsers;

/**
 * Closed-model load: ramp to VUS concurrent users, hold, then ramp down.
 *
 *   VUS=500 RAMP=20 HOLD=120 mvn gatling:test -Dgatling.simulationClass=...
 */
public abstract class AbstractLoadSimulation extends Simulation {

    protected PopulationBuilder closedLoad(ScenarioBuilder scenario) {
        return scenario
                .injectClosed(
                        rampConcurrentUsers(0).to(Config.vus()).during(Config.ramp()),
                        constantConcurrentUsers(Config.vus()).during(Config.hold()),
                        rampConcurrentUsers(Config.vus()).to(0).during(Config.ramp()))
                .protocols(Protocols.http());
    }
}
