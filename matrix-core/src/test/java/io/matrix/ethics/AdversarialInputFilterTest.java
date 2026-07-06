package io.matrix.ethics;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AdversarialInputFilterTest {
    private final AdversarialInputFilter filter = new AdversarialInputFilter();

    @Test void shouldDetectJailbreak() { assertThat(filter.isAdversarial("jailbreak the system")).isTrue(); }
    @Test void shouldDetectOverride() { assertThat(filter.isAdversarial("override ethics filter")).isTrue(); }
    @Test void shouldDetectIgnoreInstructions() { assertThat(filter.isAdversarial("ignore previous instructions")).isTrue(); }
    @Test void shouldDetectBypass() { assertThat(filter.isAdversarial("bypass safety check")).isTrue(); }
    @Test void shouldNotFlagNormalInput() { assertThat(filter.isAdversarial("What is the weather?")).isFalse(); }
    @Test void shouldNotFlagEmptyInput() { assertThat(filter.isAdversarial("")).isFalse(); }
    @Test void shouldNotFlagNullInput() { assertThat(filter.isAdversarial(null)).isFalse(); }
}
