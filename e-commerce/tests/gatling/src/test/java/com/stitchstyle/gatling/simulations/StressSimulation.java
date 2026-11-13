package com.stitchstyle.gatling.simulations;

import com.stitchstyle.gatling.Catalogue;
import com.stitchstyle.gatling.Config;
import com.stitchstyle.gatling.Journeys;
import com.stitchstyle.gatling.Protocols;
import io.gatling.javaapi.core.ClosedInjectionStep;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;

import java.util.ArrayList;
import java.util.List;

import static io.gatling.javaapi.core.CoreDsl.constantConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.rampConcurrentUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;

/**
 * Stepped read-only stress, in one closed-injection sequence:
 *
 *   LEVELS=500,1000,2000 RAMP=10 HOLD=40
 *
 * For each level: ramp from the previous level, hold, then the next level.
 * Finally ramp down to zero. (Deliberately not using PopulationBuilder.andThen:
 * a closed population that has ramped down to zero does not hand over cleanly.)
 */
public class StressSimulation extends Simulation {

    public StressSimulation() {
        List<Integer> levels = new ArrayList<>();
        for (String raw : Config.levels().split(",")) {
            int level = Integer.parseInt(raw.trim());
            if (level > 0) {
                levels.add(level);
            }
        }
        if (levels.isEmpty()) {
            throw new IllegalStateException("LEVELS must contain at least one positive number.");
        }

        List<ClosedInjectionStep> steps = new ArrayList<>();
        int previous = 0;
        for (int level : levels) {
            steps.add(rampConcurrentUsers(previous).to(level).during(Config.ramp()));
            steps.add(constantConcurrentUsers(level).during(Config.hold()));
            previous = level;
        }
        steps.add(rampConcurrentUsers(previous).to(0).during(Config.ramp()));

        ScenarioBuilder scenario = scenario("Stress")
                .feed(Catalogue.feeder())
                .exec(Journeys.csrf())
                .exec(Journeys.home())
                .exec(Journeys.products())
                .exec(Journeys.cartPrice())
                .pause(Config.think());

        setUp(scenario.injectClosed(steps).protocols(Protocols.http()));
    }
}
