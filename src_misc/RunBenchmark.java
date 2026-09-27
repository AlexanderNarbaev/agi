import io.matrix.brain.runtime.BenchmarkRunner;
import io.matrix.brain.runtime.EvalBattery;

public class RunBenchmark {
  public static void main(String[] args) throws Exception {
    BenchmarkRunner r = new BenchmarkRunner();
    BenchmarkRunner.RunReport rep = r.run(args[0], null, EvalBattery.standardBattery(), args[1]);
    System.out.println("TOTAL=" + rep.total() + " PASSED=" + rep.passed() + 
      " PASSRATE=" + rep.passRate() + " MEAN_CONF=" + rep.meanConfidence() + 
      " MEAN_LATENCY=" + rep.meanLatencyMs() + "ms");
    java.util.Map<String, int[]> byCat = new java.util.LinkedHashMap<>();
    for (var row : rep.rows()) {
      int[] arr = byCat.computeIfAbsent(row.category(), k -> new int[2]);
      arr[0]++;
      if (row.passed()) arr[1]++;
    }
    for (var e : byCat.entrySet()) {
      double rate = (double)e.getValue()[1]/e.getValue()[0];
      System.out.println("CAT " + e.getKey() + ": " + e.getValue()[1] + "/" + e.getValue()[0] + " (" + String.format("%.2f", rate) + ")");
    }
  }
}
