#!/usr/bin/env bash
# RECON-W13 — Run the frozen battery against the live MATRIX gateway.
# Usage: ./scripts/w13-live-benchmark.sh [output-csv]
set -e

OUT="${1:-data/mind/benchmarks/w13-live.csv}"
mkdir -p "$(dirname "$OUT")"

export JAVA_HOME="${JAVA_HOME:-$HOME/.sdkman/candidates/java/25.0.2-graalce}"
export PATH="$JAVA_HOME/bin:$PATH"

# Build all module classes (no need to JAR for runtime classpath)
./gradlew :matrix-brain-runtime:classes :matrix-api-gateway:classes --no-daemon --console=plain >/dev/null

# Regenerate runtime classpath if missing
if [ ! -f matrix-api-gateway/build/runtime-classpath.txt ]; then
  ./gradlew :matrix-api-gateway:writeRuntimeClasspath --no-daemon --console=plain >/dev/null
fi

# Build CLI tool
CP="$(cat matrix-api-gateway/build/runtime-classpath.txt):matrix-brain-runtime/build/classes/java/main:matrix-api-gateway/build/classes/java/main"
javac -cp "$CP" -d /tmp src_misc/RunBenchmark.java 2>/dev/null || {
  mkdir -p src_misc
  cat > src_misc/RunBenchmark.java << 'INNER'
import io.matrix.brain.runtime.BenchmarkRunner;
import io.matrix.brain.runtime.EvalBattery;
public class RunBenchmark {
  public static void main(String[] args) throws Exception {
    BenchmarkRunner r = new BenchmarkRunner();
    BenchmarkRunner.RunReport rep = r.run(args[0], null, EvalBattery.standardBattery(), args[1]);
    System.out.println("TOTAL=" + rep.total() + " PASSED=" + rep.passed() + " PASSRATE=" + rep.passRate());
  }
}
INNER
  javac -cp "$CP" -d /tmp src_misc/RunBenchmark.java
}

# Run
java -cp "/tmp:$CP" RunBenchmark http://localhost:8765 "$OUT"

# Regression check vs prior CSV
PRIOR="data/mind/benchmarks/true-w13-eval.csv"
if [ -f "$PRIOR" ]; then
  cat > src_misc/RunRegression.java << 'INNER'
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
INNER
  javac -cp "$CP" -d /tmp src_misc/RunRegression.java
  java -cp "/tmp:$CP" RunRegression "$OUT" "$PRIOR"
fi
