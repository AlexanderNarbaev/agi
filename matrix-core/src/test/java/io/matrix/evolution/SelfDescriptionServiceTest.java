package io.matrix.evolution;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SelfDescriptionServiceTest {

    @Test
    void shouldDescribeSimpleClass() {
        var svc = new SelfDescriptionService();
        var d = svc.describe(Sample.class);
        assertThat(d.simpleName()).isEqualTo("Sample");
        assertThat(d.kind()).isIn("class", "final class", "abstract class");
        assertThat(d.methods()).extracting("name").contains("doThing", "anotherMethod");
    }

    @Test
    void summaryShouldMentionDeprecatedMethods() {
        var svc = new SelfDescriptionService();
        String summary = svc.summarize(Sample.class);
        assertThat(summary).contains("Sample").contains("doThing");
        if (hasDeprecatedMethod(Sample.class)) {
            assertThat(summary).contains("[deprecated]");
        }
    }

    @Test
    void shouldHandleNullClassGracefully() {
        var svc = new SelfDescriptionService();
        var d = svc.describe(null);
        assertThat(d.simpleName()).isEqualTo("?");
    }

    @Test
    void shouldDetectFrozenKeywordInClassName() {
        var svc = new SelfDescriptionService();
        var d = svc.describe(FrozenThing.class);
        assertThat(d.markerTags()).contains("frozen");
    }

    private static boolean hasDeprecatedMethod(Class<?> c) {
        for (var m : c.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Deprecated.class)) return true;
        }
        return false;
    }

    /** Test fixture: ordinary class with one deprecated method. */
    public static class Sample {
        public String doThing() { return "ok"; }
        @Deprecated
        public void anotherMethod() {}
    }

    /** Test fixture: name embeds the FROZEN marker. */
    public static class FrozenThing {
        public int code() { return 0; }
    }
}
