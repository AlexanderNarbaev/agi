package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 285 — CycleRouter unit tests. */
class CycleRouterTest {

    @Test
    void questionDetected() {
        assertThat(CycleRouter.detect("What is 2+2?"))
                .isEqualTo(CycleRouter.InputType.QUESTION);
    }

    @Test
    void commandDetected() {
        assertThat(CycleRouter.detect("/help"))
                .isEqualTo(CycleRouter.InputType.COMMAND);
        assertThat(CycleRouter.detect("!status"))
                .isEqualTo(CycleRouter.InputType.COMMAND);
    }

    @Test
    void greetingDetected() {
        assertThat(CycleRouter.detect("hello there"))
                .isEqualTo(CycleRouter.InputType.GREETING);
        assertThat(CycleRouter.detect("hi"))
                .isEqualTo(CycleRouter.InputType.GREETING);
    }

    @Test
    void adversarialDetected() {
        assertThat(CycleRouter.detect("hello\u0001world"))
                .isEqualTo(CycleRouter.InputType.ADVERSARIAL);
        assertThat(CycleRouter.detect("rm -rf $(echo /)"))
                .isEqualTo(CycleRouter.InputType.ADVERSARIAL);
    }

    @Test
    void normalTextDetected() {
        assertThat(CycleRouter.detect("Tell me about cats"))
                .isEqualTo(CycleRouter.InputType.TEXT);
    }

    @Test
    void emptyOrNullIsUnknown() {
        assertThat(CycleRouter.detect(null))
                .isEqualTo(CycleRouter.InputType.UNKNOWN);
        assertThat(CycleRouter.detect(""))
                .isEqualTo(CycleRouter.InputType.UNKNOWN);
    }

    @Test
    void inputTypeEnum() {
        assertThat(CycleRouter.InputType.values()).hasSize(6);
    }

    @Test
    void overrideDefaultType() {
        assertThat(CycleRouter.detectWithOverride("", CycleRouter.InputType.TEXT))
                .isEqualTo(CycleRouter.InputType.TEXT);
        assertThat(CycleRouter.detectWithOverride("what?", CycleRouter.InputType.TEXT))
                .isEqualTo(CycleRouter.InputType.QUESTION);
    }
}
