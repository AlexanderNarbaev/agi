package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 302 — CycleRateLimiter unit tests. */
class CycleRateLimiterTest {

    @Test
    void initialTokensAvailable() {
        var rl = new CycleRateLimiter(10, 1.0);
        assertThat(rl.availableTokens()).isCloseTo(10.0, org.assertj.core.data.Offset.offset(0.1));
    }

    @Test
    void consumeReducesTokens() {
        var rl = new CycleRateLimiter(10, 1.0);
        assertThat(rl.tryConsume()).isTrue();
        assertThat(rl.availableTokens()).isCloseTo(9.0, org.assertj.core.data.Offset.offset(0.1));
    }

    @Test
    void blocksWhenExhausted() {
        var rl = new CycleRateLimiter(2, 0.001); // very slow refill
        rl.tryConsume();
        rl.tryConsume();
        assertThat(rl.tryConsume()).isFalse();
    }

    @Test
    void refillsOverTime() throws Exception {
        var rl = new CycleRateLimiter(2, 100.0); // fast refill
        rl.tryConsume();
        rl.tryConsume();
        Thread.sleep(50); // should refill ~5 tokens
        assertThat(rl.tryConsume()).isTrue();
    }
}
