import io.matrix.bir.BirRegistry;
import io.matrix.brain.runtime.DistillationPipeline;
public class RunActivationDistill {
  public static void main(String[] a) throws Exception {
    BirRegistry reg = new BirRegistry();
    DistillationPipeline p1 = new DistillationPipeline(42L, reg);
    int before = reg.size();
    var r1 = p1.distillFromActivations("capacities-8", a[0], 8);
    int after1 = reg.size();
    System.out.println("A samples=" + r1.samplesUsed() + " fidelity=" + r1.fidelity()
      + " hash=" + r1.artifactHash() + " delta=" + (after1 - before) + " ms=" + r1.durationMs());
    System.out.println("A prov=" + r1.provenance());
    DistillationPipeline p2 = new DistillationPipeline(42L, reg);
    var r2 = p2.distillFromActivations("capacities-8-replay", a[0], 8);
    System.out.println("B samples=" + r2.samplesUsed() + " hash=" + r2.artifactHash()
      + " registryNow=" + reg.size() + " (super-additive A+B=" + (reg.size() - before) + ")");
  }
}
