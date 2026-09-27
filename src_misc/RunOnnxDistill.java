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
