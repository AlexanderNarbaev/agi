package io.matrix.proxy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EffectorProxyTest {

    private final EffectorProxy proxy = new EffectorProxy();

    @Nested
    class BitsToAction {

        @Test
        void shouldMapAll32Codes() {
            for (int i = 0; i < 32; i++) {
                String action = proxy.bitsToAction(i);
                assertThat(action).as("action code %d", i).isNotNull().isNotEmpty();
            }
        }

        @Test
        void shouldMapKnownCodes() {
            assertThat(proxy.bitsToAction(0)).isEqualTo("IDLE");
            assertThat(proxy.bitsToAction(1)).isEqualTo("MOVE_FORWARD");
            assertThat(proxy.bitsToAction(7)).isEqualTo("JUMP");
            assertThat(proxy.bitsToAction(8)).isEqualTo("ATTACK");
            assertThat(proxy.bitsToAction(31)).isEqualTo("RESPOND");
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, -100, 32, 33, 100})
        void shouldRejectOutOfRangeCodes(int code) {
            assertThatThrownBy(() -> proxy.bitsToAction(code))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("actionCode must be in [0, 31]");
        }

        @Test
        void actionCountShouldBe32() {
            assertThat(proxy.actionCount()).isEqualTo(32);
        }

        @Test
        void allActionNamesShouldBeUnique() {
            java.util.Set<String> names = new java.util.HashSet<>();
            for (int i = 0; i < 32; i++) {
                names.add(proxy.bitsToAction(i));
            }
            assertThat(names).hasSize(32);
        }
    }

    @Nested
    class ActionToMinecraftCommand {

        @Test
        void shouldProduceCommandForAllActions() {
            for (int i = 0; i < 32; i++) {
                String action = proxy.bitsToAction(i);
                String cmd = proxy.actionToMinecraftCommand(action);
                assertThat(cmd).as("command for %s", action).isNotNull().isNotEmpty();
            }
        }

        @Test
        void shouldIncludeParamsWhenProvided() {
            String cmd = proxy.actionToMinecraftCommand("MINE", "stone");
            assertThat(cmd).isEqualTo("mine stone");
        }

        @Test
        void shouldUseDefaultWhenNoParams() {
            String cmd = proxy.actionToMinecraftCommand("MINE");
            assertThat(cmd).isEqualTo("mine block");
        }

        @Test
        void shouldHandleIdleCommand() {
            assertThat(proxy.actionToMinecraftCommand("IDLE")).isEqualTo("idle");
        }

        @Test
        void shouldRejectNullAction() {
            assertThatThrownBy(() -> proxy.actionToMinecraftCommand(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldRejectUnknownAction() {
            assertThatThrownBy(() -> proxy.actionToMinecraftCommand("FLY"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown action");
        }
    }

    @Nested
    class ActionToText {

        @Test
        void shouldProduceTextForAllCodes() {
            for (int i = 0; i < 32; i++) {
                String text = proxy.actionToText(i, "");
                assertThat(text).as("text for code %d", i).isNotNull().isNotEmpty();
            }
        }

        @Test
        void shouldProduceContextualResponse() {
            String text = proxy.actionToText(1, "the door");
            assertThat(text).contains("door");
        }

        @Test
        void shouldHandleNullContext() {
            String text = proxy.actionToText(0, null);
            assertThat(text).isEqualTo("I am waiting.");
        }

        @Test
        void shouldHandleBlankContext() {
            String text = proxy.actionToText(7, "   ");
            assertThat(text).isEqualTo("Jumping!");
        }

        @Test
        void idleTextShouldBeStatic() {
            assertThat(proxy.actionToText(0, "anything")).isEqualTo("I am waiting.");
        }

        @Test
        void respondActionShouldIncludeContext() {
            String text = proxy.actionToText(31, "I am fine");
            assertThat(text).isEqualTo("Responding: I am fine");
        }

        @Test
        void talkActionShouldIncludeContext() {
            String text = proxy.actionToText(22, "Hello there");
            assertThat(text).isEqualTo("Talking: Hello there");
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, 32, 100})
        void shouldRejectOutOfRangeCodes(int code) {
            assertThatThrownBy(() -> proxy.actionToText(code, ""))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
