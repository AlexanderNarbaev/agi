package io.matrix.cli;

import io.matrix.cognitive.CognitiveService;
import io.matrix.cognitive.CognitiveStore;
import io.matrix.cognitive.algorithms.Procedure;
import io.matrix.cognitive.report.ReportProcedure;
import io.matrix.cognitive.report.ReportProcedureLearner;
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
 * CLI command that learns a validated report procedure from the observed journeys of a
 * workspace — the producer side of CRJ-01's arm C (OD-082).
 *
 * <p><b>What it is for.</b> The learner is the mechanism that turns independently observed
 * episodes into a candidate, checks the candidate on held-out cases and promotes it with a
 * version and a provenance. It is a command rather than a route on purpose: promoting a
 * procedure is a durable write to the cognitive store, and the store has ONE writer. An
 * operator runs this command while the serving process is not holding the database, so the
 * one-writer rule is kept without a new network surface and without a second database.</p>
 *
 * <p><b>Usage:</b>
 * {@code matrix learn-report-procedure --workspace photosynthesis
 * --episodes report-a,report-b --cases cases.tsv --quarantine quarantine.txt}</p>
 *
 * <p><b>The files.</b> The cases file holds one held-out case per line, as
 * {@code <caseId><TAB><question>}. The quarantine file holds one id per line — the records
 * that are evaluation traffic. The quarantine file is REQUIRED even when it is empty: the
 * gate fails closed, and a missing list is not the same thing as an empty one.</p>
 */
@CommandLine.Command(name = "learn-report-procedure",
        description = "Learns a validated report procedure from observed journeys")
public class LearnReportProcedureCommand implements Callable<Integer> {

    @CommandLine.Option(names = {"--workspace", "-w"}, required = true,
            description = "The workspace the procedure belongs to")
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
     * <p><b>Why it has its own main.</b> The command does not need the application: it needs a
     * workbench, a store and the learner, and it builds all three itself. Booting the whole
     * application to promote one procedure would start the background trainers and write their
     * state — a side effect an operator who asks for a promotion did not ask for. This entry
     * point therefore runs the command alone, with the application's own jars on the classpath,
     * and touches nothing but the knowledge database it was pointed at.</p>
     *
     * @param args the command's arguments
     */
    public static void main(String[] args) {
        System.exit(new CommandLine(new LearnReportProcedureCommand()).execute(args));
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

        List<ReportProcedureLearner.Case> heldOut = new ArrayList<>();
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
            heldOut.add(new ReportProcedureLearner.Case(trimmed.substring(0, tab).strip(),
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
            List<ReportProcedureLearner.Observation> observations =
                    ReportProcedureLearner.observationsFrom(store, episodes);

            ReportProcedureLearner learner = new ReportProcedureLearner(bench, store);
            ReportProcedureLearner.Outcome outcome = learner.learn(workspace, observations,
                    heldOut, quarantined, Instant.now().toString());

            System.out.println("promoted: " + outcome.promoted());
            System.out.println("reason: " + outcome.reason());
            if (!outcome.promoted()) {
                return 1;
            }
            Procedure procedure = outcome.procedure();
            Set<String> support = ReportProcedure.supportOf(procedure);
            int everyClaim = bench.workspace(workspace).claims().size();
            int supportedClaims = (int) bench.workspace(workspace).claims().stream()
                    .filter(claim -> support.contains(claim.introducedBySourceId()))
                    .count();
            System.out.println("procedure: " + procedure.id() + " version " + procedure.version());
            System.out.println("validated on: " + procedure.validationCases()
                    + " held-out case(s), agreement " + procedure.validationAgreement()
                    + ", kind " + procedure.validationKind());
            System.out.println("learned in episode(s): " + procedure.learnedInEpisode());
            System.out.println("support: " + support.size() + " source(s), " + supportedClaims
                    + " of the workspace's " + everyClaim + " claim(s)");

            // the two readings of the same question, measured once and labelled as one
            // measurement — the saving's mechanism is the narrower reading, and this is the
            // effect that mechanism had here
            String question = heldOut.get(0).question();
            long fullStart = System.nanoTime();
            bench.ask(workspace, question, AbstractionLevel.INTRODUCTORY);
            long fullNanos = System.nanoTime() - fullStart;
            long restrictedStart = System.nanoTime();
            bench.ask(workspace, question, AbstractionLevel.INTRODUCTORY, support);
            long restrictedNanos = System.nanoTime() - restrictedStart;
            System.out.println("one measurement of the two readings of '" + question + "': full "
                    + (fullNanos / 1_000_000.0) + " ms, restricted "
                    + (restrictedNanos / 1_000_000.0) + " ms");
            store.close();
            return 0;
        } finally {
            bench.close();
        }
    }
}
