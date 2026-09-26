package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NyayaSyllogismTest {

    @Test
    void complete_5_member_syllogism_validates() {
        List<NyayaSyllogism.Member> m = NyayaSyllogism.empty();
        m.set(0, new NyayaSyllogism.Member("Pratijna", "proposition", "Sound is eternal"));
        m.set(1, new NyayaSyllogism.Member("Hetu", "reason", "because it is audible"));
        m.set(2, new NyayaSyllogism.Member("Drstanta", "example", "ether is audible"));
        m.set(3, new NyayaSyllogism.Member("Upanaya", "application", "this is the case for ether"));
        m.set(4, new NyayaSyllogism.Member("Nigamana", "conclusion", "hence sound is eternal"));
        assertThat(NyayaSyllogism.isComplete(m)).isTrue();
        assertThat(NyayaSyllogism.countPresent(m)).isEqualTo(5);
    }

    @Test
    void incomplete_syllogism_rejected() {
        List<NyayaSyllogism.Member> m = NyayaSyllogism.empty();
        m.set(0, new NyayaSyllogism.Member("Pratijna", "proposition", "X is Y"));
        // Hetu, Drstanta, Upanaya, Nigamana blank
        assertThat(NyayaSyllogism.isComplete(m)).isFalse();
        assertThat(NyayaSyllogism.countPresent(m)).isEqualTo(1);
    }

    @Test
    void null_input_safe() {
        assertThat(NyayaSyllogism.isComplete(null)).isFalse();
        assertThat(NyayaSyllogism.countPresent(null)).isEqualTo(0);
    }

    @Test
    void short_list_rejected() {
        List<NyayaSyllogism.Member> m = new ArrayList<>();
        m.add(new NyayaSyllogism.Member("Pratijna", "proposition", "X is Y"));
        assertThat(NyayaSyllogism.isComplete(m)).isFalse();
    }

    @Test
    void blank_member_does_not_count() {
        List<NyayaSyllogism.Member> m = NyayaSyllogism.empty();
        m.set(0, new NyayaSyllogism.Member("Pratijna", "proposition", "X is Y"));
        m.set(1, new NyayaSyllogism.Member("Hetu", "reason", "   "));
        assertThat(NyayaSyllogism.countPresent(m)).isEqualTo(1);
    }

    @Test
    void schema_is_five_members() {
        assertThat(NyayaSyllogism.SCHEMA).hasSize(5);
    }

    @Test
    void schema_member_names_match_sanskrit() {
        assertThat(NyayaSyllogism.SCHEMA.get(0).sanskrit()).isEqualTo("Pratijna");
        assertThat(NyayaSyllogism.SCHEMA.get(1).sanskrit()).isEqualTo("Hetu");
        assertThat(NyayaSyllogism.SCHEMA.get(2).sanskrit()).isEqualTo("Drstanta");
        assertThat(NyayaSyllogism.SCHEMA.get(3).sanskrit()).isEqualTo("Upanaya");
        assertThat(NyayaSyllogism.SCHEMA.get(4).sanskrit()).isEqualTo("Nigamana");
    }
}
