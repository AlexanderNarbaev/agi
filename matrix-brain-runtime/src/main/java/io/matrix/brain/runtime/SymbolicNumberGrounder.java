package io.matrix.brain.runtime;
import java.util.List;
public final class SymbolicNumberGrounder {
  public static final List<String> DEFAULT_VARIABLES = List.of("X","Y","A","B");
  public record GroundResult(String originalQuery, String substitutedQuery, String substitutedValue, int candidatesTried, boolean generalized) {}
  public GroundResult tryGeneralize(String query, java.util.function.Predicate<String> hasAnswer) {
    int[] c = {2,3,4,5,6,7,8,9,10,11,12,13,14,15};
    for (int v : c) {
      String s = query.replace("X", String.valueOf(v)).replace("Y", String.valueOf(v+1));
      if (hasAnswer.test(s)) return new GroundResult(query, s, String.valueOf(v), v-1, true);
    }
    return new GroundResult(query, query, "?", c.length, false);
  }
  public String narrative() {
    return "SymbolicNumberGrounder: " + DEFAULT_VARIABLES.size() + " variables target GENERALIZATION.";
  }
}
