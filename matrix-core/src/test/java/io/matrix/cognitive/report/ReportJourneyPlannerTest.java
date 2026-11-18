package io.matrix.cognitive.report;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

/**
 * P2 — the kernel's planner for the report journey, and the contract the BUILD checks.
 *
 * <p><b>What this is.</b> The planner the route injects. The route
 * ({@code io.matrix.knowledge.ArtifactReportResource}) takes the planner as an injected field, so
 * the container has to be able to resolve it — and while the planner was a plain class, it could
 * not: the packaged build failed with
 * {@code UnsatisfiedResolutionException: Unsatisfied dependency for type
 * io.matrix.cognitive.report.ReportJourneyPlanner}, and the application could not be built at all.
 *
 * <p><b>Why the fast lane could not see it.</b> The tests drive the route by CONSTRUCTING it (the
 * route's fields are public for that reason), so no test asked the container to resolve the
 * planner ⇒ ⇒ ⇒ the defect was invisible to every test that does not boot the container, and the
 * repository has no {@code @QuarkusTest}. It surfaced only in the release lane, which builds the
 * packaged application (the risk is recorded as R-EPIS-09).
 *
 * <p><b>What this pins.</b> The two things the build checks and the tests cannot: the planner
 * carries a <b>normal scope</b> (a client proxy needs a subclass), and the class is therefore
 * <b>not final</b>. A cheap unit test for the exact contract the build validates.
 *
 * <p><b>Why the scope is the application's.</b> The planner's own javadoc says the ledger of the
 * measured costs must outlive the request — the second journey for the same identity must see what
 * the first one cost ⇒ ⇒ ⇒ the planner is one instance per application, not per request.
 */
@DisplayName("P2 — the kernel's planner for the report journey")
class ReportJourneyPlannerTest {

    @Test
    @DisplayName("the planner the route injects is an application-scoped bean, and it can be proxied")
    void thePlannerIsAnApplicationScopedBean() {
        assertThat(ReportJourneyPlanner.class.getAnnotation(ApplicationScoped.class))
                .as("the planner the route injects must be a CDI bean, or the Quarkus build fails "
                        + "with an UnsatisfiedResolutionException")
                .isNotNull();
        assertThat(Modifier.isFinal(ReportJourneyPlanner.class.getModifiers()))
                .as("a normal-scoped bean is proxied, and a proxy needs a subclass: the planner "
                        + "must not be final, or the Quarkus build fails")
                .isFalse();
    }
}
