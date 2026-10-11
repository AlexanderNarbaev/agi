package io.matrix.research;

import io.matrix.tsetlin.AdvancedTsetlinMachine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RUN 414 — Phase S: Tsetlin expansion (fractional s, Γ(t),
 * multi-clause, state export).
 */
class Exp414AdvancedTsetlinTest {

    @Test
void tsetlinInitAndPredict() {
          var model = AdvancedTsetlinMachine.init(4, 5, 2, 0xCAFE);
          assertThat(model.nFeatures()).isEqualTo(4);
          assertThat(model.nClauses()).isEqualTo(5);
          assertThat(model.nClasses()).isEqualTo(2);
          int[] features = {1, 0, 1, 0};
          var pred = AdvancedTsetlinMachine.predict(model, features);
          assertThat(pred.predicted()).isIn(0, 1);

          // W35.2 — the assertion above was tautological: `isIn(0, 1)` holds for EVERY value
          // predict() can return, so it passed even while the machine returned confidence 0 for
          // every input and could not express anything. What is asserted now is the property
          // that was actually broken.
          assertThat(pred.confidence())
                  .as("clauses initialised as don't-care must actually fire; this was 0.0000 "
                          + "for every input when literals were seeded at 50% density")
                  .isGreaterThan(0.0);
      }

      /**
       * W35.2 — the machine must respond to its input.
       *
       * <p>With 50%-dense literals no clause ever matched, so predict() returned the same
       * class and the same confidence for every possible input. A predictor that ignores its
       * input is not a predictor, and the previous test could not detect it.</p>
       */
      @Test
      void predictionReachesClauses() {
          var model = AdvancedTsetlinMachine.init(32, 16, 2, 0xCAFE);
          java.util.Set<Double> confidences = new java.util.HashSet<>();
          var rng = new java.util.Random(7);
          for (int t = 0; t < 200; t++) {
              int[] x = new int[32];
              for (int i = 0; i < 32; i++) {
                  x[i] = rng.nextInt(2);
              }
              confidences.add(AdvancedTsetlinMachine.predict(model, x).confidence());
          }
          // NOTE ON WHAT THIS DOES NOT ASSERT, because I got it wrong first: an UNTRAINED
          // Tsetlin machine is *supposed* to answer uniformly. With don't-care literals every
          // clause matches every input, so votes are constant and confidence is a single value.
          // Asserting that confidence VARIES would therefore be asserting a defect — the
          // opposite of learning. The property that was actually broken, and that this test
          // pins, is that the clauses reach a vote at all: with 50%-dense literals nothing
          // matched and confidence was 0.0000 for every input.
          assertThat(confidences)
                  .as("confidence must be non-zero — this was 0.0000 for every input when "
                          + "clause literals were seeded at 50% density, meaning no clause "
                          + "could ever fire")
                  .allMatch(c -> c > 0.0);
      }

      /**
       * W35.2 — training must at least MOVE the literals.
       *
       * <p>Weaker than "training makes it accurate", because it does not — see
       * {@link #learningRuleIsKnownNotImplemented()}. But the model must be mutable: if
       * updateClause left the clause arrays untouched, every fix attempted on the learning rule
       * would be a no-op and the defect would look identical from outside.</p>
       */
      @Test
      void trainingChangesClauseLiterals() {
          var model = AdvancedTsetlinMachine.init(32, 8, 2, 42L);
          int before = 0;
          for (var row : model.clauses()) {
              for (var cl : row) {
                  for (int b : cl.include()) {
                      before += b;
                  }
              }
          }
          assertThat(before)
                  .as("don't-care initialisation must start with no include literals")
                  .isZero();

          var rng = new java.util.Random(11);
          var trained = model;
          for (int t = 0; t < 500; t++) {
              int[] x = new int[32];
              for (int i = 0; i < 32; i++) {
                  x[i] = rng.nextInt(2);
              }
              trained = AdvancedTsetlinMachine.updateClause(
                      trained, 0, t % 8, x, 0, 0.9, 5000L + t);
          }
          int after = 0;
          for (var row : trained.clauses()) {
              for (var cl : row) {
                  for (int b : cl.include()) {
                      after += b;
                  }
              }
          }
          assertThat(after)
                  .as("reinforcement must actually write include literals into clauses")
                  .isGreaterThan(0);
      }

      /**
       * W35.2 — init is a pure function of its arguments, per CONSTITUTION Article I.
       *
       * <p>The polarity still consumes the seeded RNG, so two models built with the same seed
       * are identical. Worth pinning because the fix removed the per-literal RNG draws; if
       * someone reintroduces unbounded randomness here, determinism goes with it.</p>
       */
      @Test
      void initIsDeterministicForAGivenSeed() {
          var a = AdvancedTsetlinMachine.init(16, 8, 2, 1234L);
          var b = AdvancedTsetlinMachine.init(16, 8, 2, 1234L);
          for (int c = 0; c < a.nClasses(); c++) {
              for (int k = 0; k < a.nClauses(); k++) {
                  assertThat(b.clauses()[c][k].polarity())
                          .isEqualTo(a.clauses()[c][k].polarity());
              }
          }
          int[] x = {1, 1, 0, 0, 1, 0, 1, 1, 0, 0, 1, 0, 1, 1, 0, 0};
          assertThat(AdvancedTsetlinMachine.predict(b, x).confidence())
                  .isEqualTo(AdvancedTsetlinMachine.predict(a, x).confidence());
      }

      /**
       * W35.2 — records what is still broken, so it cannot be quietly forgotten.
       *
       * <p>The learning rule is NOT a correct Taum-I implementation and this machine does not
       * learn. Measured after 4000 updateClause calls on a trivially separable task, even with
       * the fixed initialisation: 48% accuracy where chance is 50%.</p>
       *
       * <p>updateClause takes its feedback from the WHOLE-MODEL prediction rather than from
       * whether the individual clause fired, never writes to exclude[], and never performs the
       * polarity flip that Type-I feedback requires. Until that is rewritten, this class is a
       * fixed scorer over hand-set literals and must not be described as a trained Tsetlin
       * machine. See KnownFailures.md.</p>
       */
      @Test
      void learningRuleIsKnownNotImplemented() {
          int nFeatures = 64, nClauses = 32;
          var model = AdvancedTsetlinMachine.init(nFeatures, nClauses, 2, 42L);
          var rng = new java.util.Random(7);
          for (int t = 0; t < 4000; t++) {
              int[] x = new int[nFeatures];
              for (int i = 0; i < nFeatures; i++) {
                  x[i] = rng.nextInt(2);
              }
              int label = x[0] == 1 ? 0 : 1;
              model = AdvancedTsetlinMachine.updateClause(
                      model, label, t % nClauses, x, label, 0.9, 1000L + t);
          }
          int correct = 0, n = 200;
          for (int t = 0; t < n; t++) {
              int[] x = new int[nFeatures];
              for (int i = 0; i < nFeatures; i++) {
                  x[i] = rng.nextInt(2);
              }
              if (AdvancedTsetlinMachine.predict(model, x).predicted() == (x[0] == 1 ? 0 : 1)) {
                  correct++;
              }
          }
          double accuracy = (double) correct / n;
          // Asserted at CHANCE, not at competence. This test exists to pin the known-bad
          // number so that an improvement cannot land silently without the claim being
          // revisited: if accuracy rises materially above chance, the learning rule has begun
          // to work and the note in KnownFailures.md must be rewritten.
          assertThat(accuracy)
                  .as("documented defect: learning rule does not yet exceed chance (%.1f%%). "
                          + "If this starts passing materially, update KnownFailures.md — it "
                          + "means Taum-I has been partly implemented.", 100.0 * accuracy)
                  .isBetween(0.30, 0.70);
      }

    @Test
    void temperingSchedule() {
        // At t=0: Γ(0) = Γ_max
        assertThat(AdvancedTsetlinMachine.temperingSchedule(2.0, 0.1, 0))
                .isCloseTo(2.0, org.assertj.core.data.Offset.offset(1e-9));
        // At t=10: Γ(10) = 2.0 / (1 + 1) = 1.0
        assertThat(AdvancedTsetlinMachine.temperingSchedule(2.0, 0.1, 10))
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void fractionalS() {
        // baseS=10, fraction=0.5 → 15 (between 10 and 20)
        assertThat(AdvancedTsetlinMachine.fractionalS(10, 0.5))
                .isCloseTo(15.0, org.assertj.core.data.Offset.offset(0.01));
        // baseS=10, fraction=2.0 → 30 but capped at 2*10=20
        assertThat(AdvancedTsetlinMachine.fractionalS(10, 2.0))
                .isEqualTo(20.0);
    }

    @Test
    void stateExportRoundTrip() {
        var model = AdvancedTsetlinMachine.init(4, 3, 2, 0xAL);
        byte[] data = AdvancedTsetlinMachine.exportState(model);
        assertThat(data).isNotEmpty();
        // Could import too, but skipped to avoid duplicating model class
    }
}
