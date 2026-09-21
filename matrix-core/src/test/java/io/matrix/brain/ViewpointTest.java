package io.matrix.brain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link Viewpoint} (DESIGN-02 §Level-3 weighted ensemble).
 */
class ViewpointTest {

    private static Viewpoint.Member<String, String> member(String name, double w, double returns) {
        return new Viewpoint.Member<>(name, w, s -> returns, s -> name);
    }

    @Test
    void weightTimesScoreDecidesWinner() {
        Viewpoint<String, String> vp = new Viewpoint<String, String>()
                .add(member("weak", 1.0, 0.9))
                .add(member("strong", 2.0, 0.6));

        assertThat(vp.winner("x")).contains("strong"); // 2.0×0.6=1.2 > 0.9
        assertThat(vp.decide("x")).contains("strong");
    }

    @Test
    void tieBrokenByNameConventionSmallestWins() {
        Viewpoint<String, String> vp = new Viewpoint<String, String>()
                .add(member("alpha", 1.0, 0.5))
                .add(member("beta", 1.0, 0.5));
        // Repo-wide convention: deterministic ties resolve to the smallest id/name.
        assertThat(vp.winner("s")).contains("alpha");
    }

    @Test
    void emptyEnsembleAndDuplicates() {
        assertThat(new Viewpoint<String, String>().decide("s")).isEmpty();

        var dup = member("m", 1.0, 1.0);
        Viewpoint<String, String> vp = new Viewpoint<String, String>().add(dup);
        assertThatThrownBy(() -> vp.add(member("m", 2.0, 0.5)))
                .hasMessageContaining("duplicate");

        assertThatThrownBy(() -> member("bad", 0.0, 1.0))
                .hasMessageContaining("weight");
    }
}
