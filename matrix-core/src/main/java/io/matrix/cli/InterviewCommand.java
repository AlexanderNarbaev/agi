package io.matrix.cli;

import io.matrix.agent.AgentBrainService;
import io.matrix.training.DropFolderWatcher;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Interactive REPL — the system asks the user questions and trains on the
 * answers. Designed to feel like a conversation with a student.
 *
 * <h2>What it does</h2>
 * <ol>
 *   <li>Greets the user and asks their name</li>
 *   <li>Walks through 8+ topics that the system needs clarification on,
 *       including:
 *       <ul>
 *         <li>Domain areas of interest (science, business, art, etc.)</li>
 *         <li>Preferred response style (concise, detailed, friendly, technical)</li>
 *         <li>Feedback on a sample response</li>
 *         <li>Topics to learn more about</li>
 *         <li>Modes to enable (text, code, music, image, video)</li>
 *         <li>Personalization (name, location, language)</li>
 *         <li>Goals (research, business, education, art)</li>
 *       </ul>
 *   </li>
 *   <li>Each answer is appended to the training corpus as a Q/A pair,
 *       tagged with {@code source: "interactive"}}</li>
 *   <li>After the interview, the system reports how many pairs were
 *       captured and shows a few "I learned" statements</li>
 * </ol>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 *   ./gradlew :matrix-core:quarkusRun -- --matrix interview
 * }</pre>
 *
 * <h2>Why an interview?</h2>
 * <p>The system needs user-specific data to personalize responses. Rather
 * than passively collect clicks, it actively ASKS the user what they want.
 * This produces high-quality training pairs that capture intent.
 */
@Command(
        name = "interview",
        mixinStandardHelpOptions = true,
        description = "Interactive REPL — the system asks questions to personalize training")
public class InterviewCommand implements Runnable {

    @Option(names = {"--user"}, description = "User name (skips the name question)", defaultValue = "")
    String userName;

    @Option(names = {"--no-input"}, description = "Run with default answers (for CI / smoke test)",
            defaultValue = "false")
    boolean noInput;

    @Inject
    AgentBrainService brainService;

    @Inject
    DropFolderWatcher dropFolder;

    private final Scanner scanner = new Scanner(System.in);
    private final List<String> captured = new ArrayList<>();

    @Override
    public void run() {
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║      M.A.T.R.I.X. — Interactive Learning Session        ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("I will ask you a few questions to personalize my responses.");
        System.out.println("Type your answer and press Enter (or type 'skip' to skip).");
        System.out.println();
        System.out.println("Loaded brains: " + (brainService != null ? "yes" : "no"));
        System.out.println();

        if (noInput) {
            runDefaults();
            return;
        }

        // 1. Name
        if (userName.isBlank()) {
            userName = ask("What name should I call you?");
        } else {
            captured.add("What should I call you?|My name is " + userName + ".");
        }

        // 2. Interests
        String interests = ask("What topics interest you most? " +
                "(e.g. AI, science, business, art, music, games, history)");
        captured.add("What are your main interests?|My main interests are: " + interests + ".");

        // 3. Response style
        String style = ask("How would you like me to respond? " +
                "(concise / detailed / friendly / technical / academic)");
        captured.add("How should I respond to you?|I prefer " + style + " responses.");

        // 4. Language
        String language = ask("Which language should I prefer? (English / Russian / Chinese / mixed)");
        captured.add("What language should I use?|Please respond primarily in " + language + ".");

        // 5. Goal
        String goal = ask("What's your goal with the system? " +
                "(research / business / education / creative / personal assistance)");
        captured.add("What's your main goal?|My main goal is " + goal + ".");

        // 6. Multimodal preferences
        String modes = ask("Which capabilities do you need? " +
                "(text / code / image / music / video / all)");
        captured.add("What capabilities do you need?|I need " + modes + " capabilities.");

        // 7. Topics to learn
        String topics = ask("What specific topics should I learn more about?");
        captured.add("What should I learn more about?|Focus on: " + topics + ".");

        // 8. Feedback preference
        String feedback = ask("Will you give me feedback (thumbs up/down) on my responses? (yes/no)");
        captured.add("Will you give feedback?|I will " + feedback + " give feedback.");

        // 9. A sample exchange
        String q = ask("Ask me one question to see how I respond:");
        String a = brainService != null ? brainService.generateFromMemory(q) : null;
        if (a == null) a = "I don't have a confident answer for that yet. I'll learn.";
        System.out.println();
        System.out.println("  My response: " + truncate(a, 200));
        System.out.println();
        String rating = ask("Was that helpful? (great/ok/bad)");
        captured.add("Sample Q: " + q + "|A: " + a);
        captured.add("Was the sample response good?|It was " + rating + ".");

        // 10. A fact about the user
        String fact = ask("Tell me one thing about yourself I should remember.");
        captured.add("Tell me about yourself.|" + fact);

        // ─── Save to training corpus ───
        saveAnswersToCorpus();

        System.out.println();
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("  Interview complete! Captured " + captured.size() + " training pairs.");
        System.out.println("  I'll learn from this in the next training cycle.");
        System.out.println("═══════════════════════════════════════════════════");
    }

    private void runDefaults() {
        System.out.println("Running with default answers (--no-input mode)...");
        captured.add("What should I call you?|Alex.");
        captured.add("What are your main interests?|AI, science, technology, business.");
        captured.add("How should I respond to you?|I prefer concise technical responses.");
        captured.add("What language should I use?|English.");
        captured.add("What's your main goal?|Research and personal assistance.");
        captured.add("What capabilities do you need?|text and code.");
        captured.add("What should I learn more about?|Neural network architectures and multi-modal AI.");
        captured.add("Will you give feedback?|Yes.");
        saveAnswersToCorpus();
    }

    private String ask(String question) {
        System.out.print("  ? " + question + "\n  > ");
        System.out.flush();
        if (!scanner.hasNextLine()) {
            return "(no input)";
        }
        String line = scanner.nextLine().trim();
        if (line.isEmpty() || line.equalsIgnoreCase("skip")) {
            return "(skipped)";
        }
        return line;
    }

    private void saveAnswersToCorpus() {
        java.nio.file.Path corpusFile = java.nio.file.Path.of("models/training_data/auto_generated.jsonl");
        try {
            java.nio.file.Files.createDirectories(corpusFile.getParent());
            try (var w = java.nio.file.Files.newBufferedWriter(corpusFile,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
                for (String pair : captured) {
                    String[] parts = pair.split("\\|", 2);
                    if (parts.length != 2) continue;
                    String input = parts[0].replace("\"", "\\\"");
                    String output = parts[1].replace("\"", "\\\"");
                    w.write(String.format(
                            "{\"input\":\"%s\",\"output\":\"%s\",\"source\":\"interactive\",\"timestamp\":\"%s\"}\n",
                            input, output, java.time.Instant.now()));
                }
            }
            System.out.println("  ✓ Saved " + captured.size() + " pairs to " + corpusFile);
        } catch (java.io.IOException e) {
            System.err.println("  ✗ Failed to save corpus: " + e.getMessage());
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }
}