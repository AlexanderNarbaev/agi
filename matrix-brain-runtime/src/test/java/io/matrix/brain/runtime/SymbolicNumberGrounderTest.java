package io.matrix.brain.runtime;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class SymbolicNumberGrounderTest {
  @Test void substitutes_X() {
    SymbolicNumberGrounder g = new SymbolicNumberGrounder();
    var r = g.tryGeneralize("What is X plus 3?", q -> q.equals("What is 2 plus 3?"));
    assertThat(r.generalized()).isTrue();
    assertThat(r.substitutedQuery()).isEqualTo("What is 2 plus 3?");
  }
  @Test void no_generalize_when_no_match() {
    SymbolicNumberGrounder g = new SymbolicNumberGrounder();
    var r = g.tryGeneralize("What is X plus 3?", q -> false);
    assertThat(r.generalized()).isFalse();
  }
  @Test void narrative() {
    SymbolicNumberGrounder g = new SymbolicNumberGrounder();
    assertThat(g.narrative()).contains("GENERALIZATION");
  }
}
