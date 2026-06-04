package io.matrix.cli;

import io.matrix.noosphere.FnlPackage;
import io.matrix.noosphere.KnowledgeIndex;
import io.matrix.noosphere.NoosphereRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(
        name = "noosphere",
        mixinStandardHelpOptions = true,
        description = "Pilot #7: Noosphere — FNL exchange between instances")
public class NoosphereDemoCommand implements Callable<Integer> {

    @Option(names = {"-i", "--instances"},
            description = "Number of instances", defaultValue = "3")
    int instanceCount;

    @Override
    public Integer call() {
        System.out.println("=== Pilot #7: Noosphere — FNL Exchange Between Instances ===");

        var registry = new NoosphereRegistry();
        var index = new KnowledgeIndex(registry);

        String[][] instancePackages = {
                {"instance-A", "grid_navigation_v2", "NAVIGATION", "0.92"},
                {"instance-B", "vision_object_detect", "VISION", "0.95"},
                {"instance-A", "resource_optimizer", "ECONOMY", "0.87"},
                {"instance-C", "threat_detector", "SAFETY", "0.91"},
                {"instance-B", "social_protocol", "SOCIAL", "0.78"},
                {"instance-C", "pattern_matcher", "COGNITION", "0.94"},
        };

        System.out.println("\nPublishing FNL packages to Noosphere...");
        for (String[] p : instancePackages) {
            var pkg = FnlPackage.builder()
                    .name(p[1])
                    .type(p[2])
                    .version("1.0.0")
                    .authorInstanceId(p[0])
                    .accuracy(Double.parseDouble(p[3]))
                    .generation(100)
                    .description("FNL: " + p[1])
                    .tags(p[2].toLowerCase(), "noosphere")
                    .certified(Double.parseDouble(p[3]) >= 0.9)
                    .build();

            var result = registry.publish(pkg);
            if (result.success()) {
                index.index(result.entryId(), pkg);
                String cert = pkg.certified() ? "CERT" : "uncert";
                System.out.printf("  [%s] %s <- %s (acc=%.2f)%n",
                        cert, pkg.name(), pkg.authorInstanceId(), pkg.accuracy());
            }
        }

        System.out.printf("%nRegistry: %d entries, index: %d keywords%n",
                registry.size(), index.indexedCount());

        System.out.println("\nSearching Noosphere...");
        for (String query : new String[]{"navigation", "vision", "threat", "pattern"}) {
            var results = index.search(query);
            if (!results.isEmpty()) {
                var best = results.get(0);
                var fnl = best.fnl();
                System.out.printf("  \"%s\" → %s (by %s, acc=%.2f, relevance=%.2f)%n",
                        query, fnl.name(), fnl.authorInstanceId(),
                        fnl.accuracy(), best.relevance());
            }
        }

        System.out.println("\nPilot #7 complete. FNLs exchanged between " +
                instanceCount + " instances via Noosphere.");
        return 0;
    }
}
