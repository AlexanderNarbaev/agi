#!/usr/bin/env bash
# RECON-W14 — Real ONNX distillation pipeline.
# Runs DistillationPipeline.distillFromOnnxTeacher in a separate JVM
# (so ONNX native init doesn't block the gateway's HTTP thread).
# Usage: ./scripts/distill-onnx.sh [source] [samples=16] [inputBits=16] [teacher=data/models/teacher/teacher.onnx]
set -euo pipefail

cd "$(dirname "$0")/.."

SOURCE="${1:-cli-probe}"
SAMPLES="${2:-16}"
INPUT_BITS="${3:-16}"
TEACHER="${4:-data/models/teacher/teacher.onnx}"

# Pre-flight
if [ ! -f "$TEACHER" ]; then
  echo "ERROR: teacher ONNX not found: $TEACHER"
  echo "Generate via: python3 scripts/gen_teacher_onnx.py $TEACHER"
  exit 1
fi

# Build classpath
./gradlew :matrix-brain-runtime:classes :matrix-api-gateway:classes --no-daemon --console=plain >/dev/null
if [ ! -f matrix-api-gateway/build/runtime-classpath.txt ]; then
  ./gradlew :matrix-api-gateway:writeRuntimeClasspath --no-daemon --console=plain >/dev/null
fi
CP="$(cat matrix-api-gateway/build/runtime-classpath.txt):matrix-brain-runtime/build/classes/java/main:matrix-api-gateway/build/classes/java/main"

# Compile CLI helper
mkdir -p src_misc
cat > src_misc/RunOnnxDistill.java << 'JAVA'
import io.matrix.brain.runtime.DistillationPipeline;
import io.matrix.bir.BirRegistry;
public class RunOnnxDistill {
  public static void main(String[] args) throws Exception {
    String source = args.length > 0 ? args[0] : "cli-probe";
    int samples = args.length > 1 ? Integer.parseInt(args[1]) : 16;
    int inputBits = args.length > 2 ? Integer.parseInt(args[2]) : 16;
    String teacher = args.length > 3 ? args[3] : "data/models/teacher/teacher.onnx";
    long t = System.currentTimeMillis();
    DistillationPipeline pipe = new DistillationPipeline(new BirRegistry());
    DistillationPipeline.RunResult r = pipe.distillFromOnnxTeacher(
      source, java.nio.file.Path.of(teacher), inputBits, samples);
    System.out.println("engine=OnnxActivationTeacher+Distiller.synthesize+BirRegistry.register");
    System.out.println("source=" + source);
    System.out.println("samples=" + r.samplesUsed());
    System.out.println("fidelity=" + String.format("%.4f", r.fidelity()));
    System.out.println("duration_ms=" + (System.currentTimeMillis() - t));
    System.out.println("provenance=" + r.provenance());
  }
}
JAVA
javac -cp "$CP" -d /tmp src_misc/RunOnnxDistill.java

# Run ONNX distillation in dedicated JVM (--enable-native-access for ONNX native load)
java --enable-native-access=ALL-UNNAMED \
     -Donnxruntime.disable_telemetry=true \
     -cp "/tmp:$CP" RunOnnxDistill "$SOURCE" "$SAMPLES" "$INPUT_BITS" "$TEACHER" 2>&1

# Disk ledger
echo "{\"op\":\"W14-distill\",\"source\":\"$SOURCE\",\"samples\":$SAMPLES,\"input_bits\":$INPUT_BITS,\"teacher\":\"$TEACHER\",\"free_gb\":$(df -BG . | tail -1 | awk '{print $4}' | sed 's/G//')}" >> data/DISK-LEDGER.ndjson
