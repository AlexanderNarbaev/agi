package io.matrix.cli;

import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.CognitiveStore;
import io.matrix.cognitive.report.LearnedClaim;
import io.matrix.cognitive.report.ReportClaimLearner;
import io.matrix.knowledge.AbstractionLevel;
import io.matrix.knowledge.KnowledgeWorkbench;
import picocli.CommandLine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * CLI command that learns a validated LEARNED CLAIM from the observed journeys of a
 * workspace — the producer side of CRJ-01's arm C, second half (OD-082).
 *
 * <p><b>What it is for.</b> The command is the operator's path to the second half of arm C: it
 * turns independently observed episodes into a candidate claim, checks the candidate on
 * held-out questions it was never proposed from, checks that the candidate's proposition is a
 * <b>verbatim span of its source's text</b>, and promotes the candidate with a version and a
 * provenance — or refuses it, naming the gate that refused it.</p>
 *
 * <p><b>Why it is a command rather than a route.</b> The same reason as the procedure
 * learner's command: promoting a claim is a durable write to the cognitive store, and the
 * store has ONE writer. An operator runs this while the serving process is not holding the
 * database, so the one-writer rule is kept without a new network surface and without a second
 * database.</p>
 *
 * <p><b>Usage:</b>
 * {@code matrix learn-report-claim --workspace research-pool
 * --episodes report-a,report-b --cases cases.tsv --quarantine quarantine.txt}</p>
 *
 * <p><b>The files.</b> The cases file holds one held-out case per line, as
 * {@code <caseId><TAB><question>}. The quarantine file holds one id per line — the records
 * that are evaluation traffic. The quarantine file is REQUIRED even when it is empty: the
 * gate fails closed, and a missing list is not the same thing as an empty one.</p>
 */
@CommandLine.Command(name = "learn-report-claim",
        description = "Learns a validated learned claim from observed journeys")
public class LearnReportClaimCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"--workspace", "-w"}, required = true,
            description = "The workspace the claim belongs to")
    String workspace;

    @CommandLine.Option(names = {"--episodes", "-e"}, required = true, split = ",",
            description = "The observed episodes, comma-separated")
    List<String> episodes = new ArrayList<>();

    @CommandLine.Option(names = {"--cases", "-c"}, required = true,
            description = "The held-out cases file: one '<caseId><TAB><question>' per line")
    String casesFile;

    @CommandLine.Option(names = {"--quarantine", "-q"}, required = true,
            description = "The quarantine file: one quarantined id per line; it may be empty, "
                    + "but it must be given")
    String quarantineFile;

    @CommandLine.Option(names = {"--knowledge-path", "-k"},
            defaultValue = "${env:MATRIX_KNOWLEDGE_PATH}",
            description = "The knowledge database the journeys' traces are in")
    String knowledgePath;

    /**
     * The command as a plain program.
     *
     * <p><b>Why it has its own main.</b> The command needs a workbench, a store and the
     * learner, and it builds all three itself. Booting the whole application to promote one
     * claim would start the background trainers and write their state — a side effect an
     * operator who asks for a promotion did not ask for. This entry point therefore runs the
     * command alone, with the application's own jars on the classpath, and touches nothing but
     * the knowledge database it was pointed at.</p>
     *
     * @param args the command's arguments
     */
    public static void main(String[] args) {
        System.exit(new CommandLine(new LearnReportClaimCommand()).execute(args));
    }

    @Override
    public Integer call() throws Exception {
        Path cases = Path.of(casesFile);
        Path quarantine = Path.of(quarantineFile);
        if (!Files.isRegularFile(cases)) {
            System.out.println("the cases file is missing: " + cases);
            return 2;
        }
        if (!Files.isRegularFile(quarantine)) {
            System.out.println("the quarantine file is missing: " + quarantine
                    + " — the gate fails closed, so nothing can be admitted without it");
            return 2;
        }
        if (knowledgePath == null || knowledgePath.isBlank()) {
            System.out.println("no knowledge path was given: set MATRIX_KNOWLEDGE_PATH");
            return 2;
        }

        List<ReportClaimLearner.Case> heldOut = new ArrayList<>();
        for (String line : Files.readAllLines(cases, StandardCharsets.UTF_8)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int tab = trimmed.indexOf('\t');
            if (tab <= 0) {
                System.out.println("the case line is malformed: " + trimmed);
                return 2;
            }
            heldOut.add(new ReportClaimLearner.Case(trimmed.substring(0, tab).strip(),
                    trimmed.substring(tab + 1).strip(), AbstractionLevel.INTRODUCTORY));
        }
        Set<String> quarantined = new LinkedHashSet<>();
        for (String line : Files.readAllLines(quarantine, StandardCharsets.UTF_8)) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                quarantined.add(trimmed);
            }
        }

        KnowledgeWorkbench bench = KnowledgeWorkbench.at(knowledgePath);
        try {
            CognitiveService cognitive = new CognitiveService();
            cognitive.workbench(bench);
            CognitiveStore store = cognitive.store();
            List<ReportClaimLearner.Observation> observations =
                    ReportClaimLearner.observationsFrom(store, episodes);

            ReportClaimLearner learner = new ReportClaimLearner(bench, store);
            ReportClaimLearner.Outcome outcome = learner.learn(workspace, observations,
                    heldOut, quarantined, Instant.now().toString());

            System.out.println("promoted: " + outcome.promoted());
            System.out.println("reason: " + outcome.reason());
            if (!outcome.promoted()) {
                return 1;
            }
            LearnedClaim claim = outcome.claim();
            System.out.println("learned claim: " + claim.id() + " version " + claim.version());
            System.out.println("claim: " + claim.claimId());
            System.out.println("proposition: " + claim.proposition());
            System.out.println("source: " + claim.sourceId());
            System.out.println("concept: " + claim.conceptId());
            System.out.println("validated on: " + claim.validationCases() + " held-out case(s), "
                    + "agreement " + claim.agreement() + ", kind " + claim.validationKind());
            System.out.println("learned in episode(s): " + claim.learnedInEpisode());
            System.out.println("note: " + claim.validationNote());
            store.close();
            return 0;
        } finally {
            bench.close();
        }
    }
}
