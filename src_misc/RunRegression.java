import io.matrix.brain.runtime.BenchmarkRegression;
import java.nio.file.*;
public class RunRegression {
  public static void main(String[] args) throws Exception {
    BenchmarkRegression r = new BenchmarkRegression();
    var rep = r.compare(Path.of(args[0]), Path.of(args[1]));
    System.out.println("REGRESSION=" + rep.hasRegression());
    for (var c : rep.byCategory()) System.out.printf("%s %.2f->%.2f (%+0.3f)%n", c.category(), c.ratePrev(), c.rateCurr(), c.delta());
  }
}
