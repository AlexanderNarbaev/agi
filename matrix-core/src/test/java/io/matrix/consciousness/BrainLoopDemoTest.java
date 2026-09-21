package io.matrix.consciousness;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 159 — BrainLoopDemo unit tests. */
class BrainLoopDemoTest {

    @Test
    void cliRunsWithArgAndExits() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(out));
        try {
            BrainLoopDemo.main(new String[]{"What is 2+2?"});
        } finally {
            System.setOut(original);
        }
        String output = out.toString();
        assertThat(output).contains("[brain-loop]");
        assertThat(output).contains("ACCEPT");
    }

    @Test
    void cliReadsFromStdinAndQuits() {
        ByteArrayInputStream in = new ByteArrayInputStream(
                "hello\nquit\n".getBytes());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setIn(in);
        PrintStream original = System.out;
        System.setOut(new PrintStream(out));
        try {
            BrainLoopDemo.main(new String[]{});
        } finally {
            System.setIn(System.in);
            System.setOut(original);
        }
        String output = out.toString();
        assertThat(output).contains("[brain-loop]");
        // Should have processed "hello" before quitting
        assertThat(output).contains("action=");
    }

    @Test
    void demoDenyForAdversarialInput() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(out));
        try {
            // Adversarial char triggers ActionGate.DENY at STEP_ADVERSARIAL
            BrainLoopDemo.main(new String[]{"hi\u0001there"});
        } finally {
            System.setOut(original);
        }
        String output = out.toString();
        assertThat(output).contains("DENY");
    }
}
