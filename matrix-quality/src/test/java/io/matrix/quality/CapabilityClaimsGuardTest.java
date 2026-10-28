package io.matrix.quality;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RECON-W38.4 — the published capability claims must match the registry.
 *
 * <p>{@code docs-v2/index.md} is a published page. It advertised numbers that
 * no measurement in this repository produced: "724 Tests Passing" (the figure
 * appears nowhere, and is also the tokenizer's vocabulary size),
 * "33,000× Faster than LLMs" (the benchmark report records 100,000×, and the
 * 33,000× figure was derived by dividing tokens/sec by a latency), a 25%
 * random baseline (the report measured 30%), and "90% accuracy retention from
 * LLM distillation" (no such measurement exists).</p>
 *
 * <p>{@code docs-v2/architecture/CAPABILITY-REGISTRY.md} is the authoritative
 * record of what is actually claimed and what backs it. This test keeps the
 * two in step, so a claim cannot quietly drift back:</p>
 * <ol>
 *   <li>the front page must link to the registry;</li>
 *   <li>the retired values must not reappear on the page;</li>
 *   <li>every number the page presents as a headline figure must appear in
 *       the registry, so a new number cannot be published without being
 *       registered;</li>
 *   <li>the registry must carry a row for every claim.</li>
 * </ol>
 */
class CapabilityClaimsGuardTest {

    private static final String FRONT_PAGE = "docs-v2/index.md";
    private static final String REGISTRY = "docs-v2/architecture/CAPABILITY-REGISTRY.md";

    /**
     * Values that were published without a measurement and have been retired.
     * If one of them reappears on the front page, the correction was undone.
     */
    private static final List<String> RETIRED_VALUES = List.of(
        "724",            // the "Tests Passing" figure: no measurement produced it
        "33,000",         // the "faster than LLMs" figure: matches no measurement
        "25% random"      // the logic baseline: the report measured 30%
    );

    @Test
    void the_front_page_links_to_the_capability_registry() throws IOException {
        String page = Files.readString(repoRoot().resolve(FRONT_PAGE));
        assertThat(page)
            .as("the published front page must point at the registry that records what backs it")
            .contains("CAPABILITY-REGISTRY");
    }

    @Test
    void retired_claims_do_not_reappear_on_the_front_page() throws IOException {
        String page = Files.readString(repoRoot().resolve(FRONT_PAGE));
        List<String> present = new ArrayList<>();
        for (String value : RETIRED_VALUES) {
            if (page.contains(value)) {
                present.add(value);
            }
        }
        assertThat(present)
            .as("these values were published without a measurement and have been retired; "
                + "the capability registry records what replaced them")
            .isEmpty();
    }

    @Test
    void every_headline_number_on_the_front_page_is_registered() throws IOException {
        Path root = repoRoot();
        String page = Files.readString(root.resolve(FRONT_PAGE));
        String registry = Files.readString(root.resolve(REGISTRY));

        Set<String> headlineNumbers = new LinkedHashSet<>();
        Matcher stat = Pattern.compile("stat-number\">([^<]+)<").matcher(page);
        while (stat.find()) {
            headlineNumbers.add(stat.group(1).trim());
        }
        Matcher bench = Pattern.compile("benchmark-value\">([^<]+)<").matcher(page);
        while (bench.find()) {
            headlineNumbers.add(bench.group(1).trim());
        }
        assertThat(headlineNumbers)
            .as("the front page must present headline figures - if this is empty the page "
                + "was restructured and this guard needs to be re-pointed")
            .isNotEmpty();

        List<String> unregistered = new ArrayList<>();
        for (String number : headlineNumbers) {
            if (!registry.contains(number)) {
                unregistered.add(number);
            }
        }
        assertThat(unregistered)
            .as("every headline number on the front page must be recorded in the capability "
                + "registry with its evidence")
            .isEmpty();
    }

    @Test
    void the_registry_covers_every_claim() throws IOException {
        String registry = Files.readString(repoRoot().resolve(REGISTRY));
        List<String> missing = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String id = String.format("C-%02d", i);
            if (!registry.contains(id)) {
                missing.add(id);
            }
        }
        assertThat(missing)
            .as("the capability registry must carry a row for every claim")
            .isEmpty();
    }

    /** Walks up to the directory that contains settings.gradle. */
    private static Path repoRoot() {
        Path p = Paths.get(".").toAbsolutePath();
        for (int i = 0; i < 6 && p != null; i++) {
            if (Files.exists(p.resolve("settings.gradle"))
                || Files.exists(p.resolve("settings.gradle.kts"))) {
                return p;
            }
            p = p.getParent();
        }
        return Paths.get(".").toAbsolutePath();
    }
}
