package io.matrix.bir;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link JvmSimdBackend} (SPEC-002 FR-D1): the JVM reference
 * backend must agree with {@link BooleanRuntime} on every BIR form,
 * in single and batch mode.
 */
class JvmSimdBackendTest {

    private final JvmSimdBackend backend = new JvmSimdBackend();

    /** Parity-4: no BDD reduction skips, eval is trivially correct. */
    private static TtForm parity4() {
        return new TtForm(4, new long[]{0b0110100110010110L}, "test-parity", 1.0);
    }

    /** f(x0,x1,x2,x3) = x0 & x3: irrelevant intermediate bits on some paths. */
    private static TtForm withIrrelevantVars() {
        long table = 0;
        for (int i = 0; i < 16; i++) {
            if ((i & 1) == 1 && (i & 8) == 8) table |= (1L << i);
        }
        return new TtForm(4, new long[]{table}, "test-irrelevant", 1.0);
    }

    private static long[][] allInputs(int k) {
        int size = 1 << k;
        long[][] inputs = new long[size][1];
        for (int i = 0; i < size; i++) inputs[i][0] = i;
        return inputs;
    }

    @Test
    void idAndCapabilities() {
        assertThat(backend.id()).isEqualTo("jvm-simd");
        SubstrateBackend.Capabilities caps = backend.capabilities();
        assertThat(caps.supportsBatch()).isTrue();
        assertThat(caps.supportsCompile()).isFalse();
        assertThat(caps.maxInputBits()).isEqualTo(BirLimits.DEFAULT_MAX_LITERALS);
        assertThat(caps.description()).isNotBlank();
    }

    @Test
    void compileIsUnsupported() {
        assertThatThrownBy(() -> backend.compile(parity4()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void evaluateMatchesBooleanRuntimeOnAllForms() {
        TtForm tt = parity4();
        BddForm bdd = BirCompiler.ttToBdd(tt);
        ClauseSetForm cs = BirCompiler.ttToClauseSet(tt);
        long[][] inputs = allInputs(4);

        for (BirForm form : List.of(tt, bdd, cs)) {
            long[][] fromBackend = backend.evaluate(form, inputs);
            long[][] fromRuntime = BooleanRuntime.evaluateBatch(form, inputs);
            assertThat(fromBackend).as("form %s", form.form()).isDeepEqualTo(fromRuntime);
            for (long[] in : inputs) {
                assertThat(BooleanRuntime.evaluate(form, in))
                        .as("single eval, form %s", form.form())
                        .isEqualTo(BooleanRuntime.evaluate(form, in));
            }
        }
    }

    @Test
    void evaluateMatchesBooleanRuntimeForIrrelevantVariables() {
        // Function with irrelevant intermediate variables: the BDD reduction
        // skips levels; backend and runtime must still agree bit-for-bit.
        TtForm tt = withIrrelevantVars();
        BddForm bdd = BirCompiler.ttToBdd(tt);
        long[][] inputs = allInputs(4);
        assertThat(backend.evaluate(bdd, inputs))
                .isDeepEqualTo(BooleanRuntime.evaluateBatch(bdd, inputs));
        // and the BDD now evaluates the same function as the source TT
        assertThat(backend.evaluate(bdd, inputs))
                .isDeepEqualTo(backend.evaluate(tt, inputs));
    }

    @Test
    void evaluateOutputShape() {
        long[][] outputs = backend.evaluate(parity4(), allInputs(4));
        assertThat(outputs).hasDimensions(16, 1);
    }

    @Test
    void evaluateEmptyBatch() {
        long[][] outputs = backend.evaluate(parity4(), new long[0][1]);
        assertThat(outputs).isEmpty();
    }
}
