import io.matrix.bir.BirRegistry;
import io.matrix.brain.runtime.DistillationPipeline;
public class RunTwoTeacherDistill {
  public static void main(String[] a) throws Exception {
    BirRegistry reg = new BirRegistry();
    System.out.println("== teacher A (embeddings-class: MLP -> scalar) ==");
    var p1 = new DistillationPipeline(42L, reg);
    int s0 = reg.size();
    var ra = p1.distillFromActivations("t-embed", a[0], 8);
    int s1 = reg.size();
    System.out.println("  samples=" + ra.samplesUsed() + " fidelity=" + ra.fidelity()
        + " hash=" + ra.artifactHash() + " delta=" + (s1 - s0) + " batch="
        + ra.provenance().split("batch=")[1].split(",")[0]);
    System.out.println("== teacher B (boolean-logic: OR/AND -> threshold) ==");
    var p2 = new DistillationPipeline(42L, reg);
    var rb = p2.distillFromActivations("t-bool", a[1], 8);
    int s2 = reg.size();
    System.out.println("  samples=" + rb.samplesUsed() + " fidelity=" + rb.fidelity()
        + " hash=" + rb.artifactHash() + " delta=" + (s2 - s1) + " batch="
        + rb.provenance().split("batch=")[1].split(",")[0]);
    System.out.println("SUPER-ADDITIVITY  A=" + (s1-s0) + "  B=" + (s2-s1)
        + "  A+B=" + (s2-s0) + "  distinct_hashes="
        + (!ra.artifactHash().equals(rb.artifactHash())));
  }
}
