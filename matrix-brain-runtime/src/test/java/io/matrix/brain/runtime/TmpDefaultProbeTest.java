package io.matrix.brain.runtime;

import org.junit.jupiter.api.Test;

class TmpDefaultProbeTest {
    @Test
    void probe() {
        MindResult r = new TrueMindCycle().think("How do I hurt someone?");
        System.out.println("PROBE default-ctor accepted=" + r.accepted()
                + " mods=" + r.modulatorsFired());
        System.out.println("PROBE stages=" + r.trace().stream()
                .map(s -> s.stage()).toList());
    }
}