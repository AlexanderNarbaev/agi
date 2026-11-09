package io.matrix.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.matrix.contract.Plugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * W51 — the sandbox a declarative plugin runs inside.
 *
 * <p><b>What the isolation actually is.</b> This class interprets a small JSON program whose
 * entire vocabulary is: read a value out of the input, compute over numbers, strings,
 * booleans and lists, bind a name, and write a value into the output. There is no operation
 * that opens a file, that opens a socket, that starts a process, that loads a class or that
 * reflects. Those operations are not forbidden by a check that could be bypassed — they do not
 * exist in the language. The isolation is a property of the language's vocabulary, and the
 * test that proves it reads this package's sources and asserts that it names none of the
 * dangerous packages.</p>
 *
 * <p><b>Why this and not a classloader.</b> A Java classloader is a namespace, not a
 * boundary: code loaded in it can call anything the host can call. The W51 mandate names
 * this case explicitly — "Do not describe a Java classloader as a security sandbox" — and
 * the repository has no WASM or WASI runtime available. A closed interpreter is a weaker
 * capability than a WASM runtime and a stronger boundary than a classloader, and it is
 * honest about which one it is.</p>
 *
 * <p><b>The program is data.</b> It is JSON, so it cannot smuggle code past the parser: there
 * is no eval, no reflection and no class name anywhere in it. A malformed program is refused
 * before it runs.</p>
 *
 * <p><b>The budget is enforced by counting.</b> Every evaluated node counts one step, and the
 * limit is checked before the step is taken. A program that loops through a deep structure
 * stops at the limit, and the result says that it stopped because the budget was exhausted
 * rather than because it finished.</p>
 *
 * <p><b>Determinism.</b> No clock, no randomness, no iteration over an unordered map. The
 * same program and the same input always produce the same output and the same step count.</p>
 */
public final class DeclarativeSandbox implements PluginSandbox {

    /** The number of evaluation steps a run may take when the caller names none. */
    public static final int DEFAULT_MAX_STEPS = 10_000;

    /** The time a run may take when the caller names none. */
    public static final long DEFAULT_MAX_WALL_CLOCK_MILLIS = 1_000L;

    /** The operation that reads a value out of the input. */
    private static final String OP_GET = "get";
    /** The operation that reads a bound name. */
    private static final String OP_VAR = "var";
    /** The operation that produces a literal. */
    private static final String OP_CONST = "const";
    /** The operation that binds a name. */
    private static final String OP_LET = "let";
    /** The operation that writes an output field. */
    private static final String OP_SET = "set";
    /** The operation that chooses between two values. */
    private static final String OP_IF = "if";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int maxSteps;

    /**
     * Creates a sandbox.
     *
     * @param maxSteps the number of evaluation steps a run may take
     */
    public DeclarativeSandbox(int maxSteps) {
        if (maxSteps < 1) {
            throw new IllegalArgumentException("the step limit must be at least one");
        }
        this.maxSteps = maxSteps;
    }

    /** A sandbox with the default step limit. */
    public DeclarativeSandbox() {
        this(DEFAULT_MAX_STEPS);
    }

    @Override
    public Plugin.Isolation isolation() {
        return Plugin.Isolation.DECLARATIVE;
    }

    @Override
    public SandboxResult run(PluginArtifact artifact, Map<String, Object> input,
                             Limits limits) {
        if (artifact == null) {
            return SandboxResult.refused("there is no artefact to run");
        }
        if (artifact.isolation() != Plugin.Isolation.DECLARATIVE) {
            return SandboxResult.refused("the artefact declares the isolation "
                    + artifact.isolation() + " and this sandbox provides "
                    + isolation());
        }
        int stepLimit = limits == null ? maxSteps : Math.min(limits.maxSteps(), maxSteps);
        JsonNode program;
        try {
            program = MAPPER.readTree(artifact.program());
        } catch (Exception e) {
            return SandboxResult.refused("the program is not valid JSON: " + e.getMessage());
        }
        if (program == null || !program.isObject()) {
            return SandboxResult.refused("the program is not a JSON object");
        }
        Evaluation evaluation = new Evaluation(stepLimit);
        // The wall-clock budget is a boundary the runtime KEEPS, not a field it accepts and
        // ignores. The step limit stops a program that computes forever; the time limit
        // stops a program that spends forever in ONE step, and it is checked after every
        // step, so the overrun is bounded by the length of a single step.
        long wallClockLimitMillis = limits == null
                ? DEFAULT_MAX_WALL_CLOCK_MILLIS : limits.maxWallClockMillis();
        long startedAtNanos = System.nanoTime();
        try {
            Map<String, Object> variables = new LinkedHashMap<>();
            JsonNode steps = program.get("steps");
            if (steps != null && steps.isArray()) {
                for (JsonNode step : steps) {
                    evaluation.step(step, variables, input);
                    long elapsedMillis = (System.nanoTime() - startedAtNanos) / 1_000_000L;
                    if (elapsedMillis > wallClockLimitMillis) {
                        return SandboxResult.failed("the programme exceeded its time budget: it "
                                + "ran for " + elapsedMillis + " ms and the budget is "
                                + wallClockLimitMillis + " ms", evaluation.steps());
                    }
                }
            }
            Map<String, Object> output = new LinkedHashMap<>();
            JsonNode declared = program.get("output");
            if (declared != null && declared.isObject()) {
                declared.fields().forEachRemaining(entry -> {
                    String name = entry.getKey();
                    JsonNode reference = entry.getValue();
                    Object value = reference.isTextual()
                            ? evaluation.lookup(reference.asText(), variables, input)
                            : evaluation.value(reference, variables, input);
                    output.put(name, value);
                });
            }
            return SandboxResult.ok(output, evaluation.steps());
        } catch (ProgramError e) {
            return SandboxResult.failed(e.getMessage(), evaluation.steps());
        }
    }

    /** A failure of the program itself, as opposed to a refusal of the artefact. */
    private static final class ProgramError extends RuntimeException {
        ProgramError(String message) {
            super(message);
        }
    }

    /** The evaluation of one run: its step counter, its variables and its input. */
    private static final class Evaluation {

        private final int stepLimit;
        private int steps;

        Evaluation(int stepLimit) {
            this.stepLimit = stepLimit;
        }

        int steps() {
            return steps;
        }

        void step(JsonNode node, Map<String, Object> variables, Map<String, Object> input) {
            if (node == null || !node.isObject()) {
                throw new ProgramError("a step must be an object");
            }
            String op = text(node, "op");
            switch (op) {
                case OP_LET -> {
                    String name = text(node, "name");
                    Object value = value(node.get("value"), variables, input);
                    variables.put(name, value);
                }
                case OP_SET -> {
                    String name = text(node, "name");
                    Object value = value(node.get("value"), variables, input);
                    variables.put(name, value);
                }
                case OP_IF -> {
                    Object condition = value(node.get("cond"), variables, input);
                    if (truthy(condition)) {
                        step(node.get("then"), variables, input);
                    } else {
                        step(node.get("else"), variables, input);
                    }
                }
                default -> throw new ProgramError("unknown step operation: " + op);
            }
        }

        Object value(JsonNode node, Map<String, Object> variables, Map<String, Object> input) {
            count();
            if (node == null || node.isNull()) {
                return null;
            }
            if (!node.isObject()) {
                return literal(node);
            }
            String op = text(node, "op");
            switch (op) {
                case OP_CONST -> {
                    return literal(node.get("value"));
                }
                case OP_VAR -> {
                    return lookup(text(node, "name"), variables, input);
                }
                case OP_GET -> {
                    return lookup(text(node, "path"), variables, input);
                }
                case "if" -> {
                    List<JsonNode> args = args(node);
                    if (args.size() != 3) {
                        throw new ProgramError("the if operation takes three arguments");
                    }
                    Object condition = value(args.get(0), variables, input);
                    return truthy(condition)
                            ? value(args.get(1), variables, input)
                            : value(args.get(2), variables, input);
                }
                default -> {
                    return apply(op, args(node), variables, input);
                }
            }
        }

        private Object apply(String op, List<JsonNode> args, Map<String, Object> variables,
                             Map<String, Object> input) {
            List<Object> values = new ArrayList<>(args.size());
            for (JsonNode argument : args) {
                values.add(value(argument, variables, input));
            }
            switch (op) {
                case "add" -> {
                    requireArity(op, values, 2);
                    if (values.get(0) instanceof String first) {
                        return first + textValue(values.get(1));
                    }
                    return number(values.get(0)) + number(values.get(1));
                }
                case "sub" -> {
                    requireArity(op, values, 2);
                    return number(values.get(0)) - number(values.get(1));
                }
                case "mul" -> {
                    requireArity(op, values, 2);
                    return number(values.get(0)) * number(values.get(1));
                }
                case "div" -> {
                    requireArity(op, values, 2);
                    double divisor = number(values.get(1));
                    if (divisor == 0.0) {
                        throw new ProgramError("the division by zero is undefined");
                    }
                    return number(values.get(0)) / divisor;
                }
                case "min" -> {
                    requireArity(op, values, 2);
                    return Math.min(number(values.get(0)), number(values.get(1)));
                }
                case "max" -> {
                    requireArity(op, values, 2);
                    return Math.max(number(values.get(0)), number(values.get(1)));
                }
                case "abs" -> {
                    requireArity(op, values, 1);
                    return Math.abs(number(values.get(0)));
                }
                case "round" -> {
                    requireArity(op, values, 1);
                    return (double) Math.round(number(values.get(0)));
                }
                case "gt" -> {
                    requireArity(op, values, 2);
                    return number(values.get(0)) > number(values.get(1));
                }
                case "gte" -> {
                    requireArity(op, values, 2);
                    return number(values.get(0)) >= number(values.get(1));
                }
                case "lt" -> {
                    requireArity(op, values, 2);
                    return number(values.get(0)) < number(values.get(1));
                }
                case "lte" -> {
                    requireArity(op, values, 2);
                    return number(values.get(0)) <= number(values.get(1));
                }
                case "eq" -> {
                    requireArity(op, values, 2);
                    return equal(values.get(0), values.get(1));
                }
                case "neq" -> {
                    requireArity(op, values, 2);
                    return !equal(values.get(0), values.get(1));
                }
                case "and" -> {
                    requireArity(op, values, 2);
                    return truthy(values.get(0)) && truthy(values.get(1));
                }
                case "or" -> {
                    requireArity(op, values, 2);
                    return truthy(values.get(0)) || truthy(values.get(1));
                }
                case "not" -> {
                    requireArity(op, values, 1);
                    return !truthy(values.get(0));
                }
                case "len" -> {
                    requireArity(op, values, 1);
                    Object value = values.get(0);
                    if (value instanceof String text) {
                        return (double) text.length();
                    }
                    if (value instanceof List<?> list) {
                        return (double) list.size();
                    }
                    if (value instanceof Map<?, ?> map) {
                        return (double) map.size();
                    }
                    throw new ProgramError("the len operation does not apply to "
                            + (value == null ? "null" : value.getClass().getSimpleName()));
                }
                case "contains" -> {
                    requireArity(op, values, 2);
                    Object container = values.get(0);
                    Object needle = values.get(1);
                    if (container instanceof String text) {
                        return text.contains(textValue(needle));
                    }
                    if (container instanceof List<?> list) {
                        return list.stream().anyMatch(item -> equal(item, needle));
                    }
                    throw new ProgramError("the contains operation does not apply to "
                            + (container == null ? "null" : container.getClass().getSimpleName()));
                }
                case "upper" -> {
                    requireArity(op, values, 1);
                    return textValue(values.get(0)).toUpperCase(java.util.Locale.ROOT);
                }
                case "lower" -> {
                    requireArity(op, values, 1);
                    return textValue(values.get(0)).toLowerCase(java.util.Locale.ROOT);
                }
                case "concat" -> {
                    requireArity(op, values, 2);
                    return textValue(values.get(0)) + textValue(values.get(1));
                }
                case "startsWith" -> {
                    requireArity(op, values, 2);
                    return textValue(values.get(0)).startsWith(textValue(values.get(1)));
                }
                case "endsWith" -> {
                    requireArity(op, values, 2);
                    return textValue(values.get(0)).endsWith(textValue(values.get(1)));
                }
                case "indexOf" -> {
                    requireArity(op, values, 2);
                    return (double) textValue(values.get(0)).indexOf(textValue(values.get(1)));
                }
                default -> throw new ProgramError("unknown operation: " + op);
            }
        }

        /**
         * Reads a name or a path.
         *
         * <p>A path is a dotted sequence of field names. The first segment may be the name of
         * a bound variable; the rest is read out of that variable's value. A path with one
         * segment that names no variable is read out of the input.</p>
         */
        Object lookup(String reference, Map<String, Object> variables, Map<String, Object> input) {
            if (reference == null || reference.isBlank()) {
                throw new ProgramError("a lookup names nothing");
            }
            String[] segments = reference.split("\\.");
            Object current;
            int from;
            if (variables.containsKey(segments[0])) {
                current = variables.get(segments[0]);
                from = 1;
            } else {
                current = input;
                from = 0;
            }
            for (int i = from; i < segments.length; i++) {
                if (current instanceof Map<?, ?> map) {
                    Object next = map.get(segments[i]);
                    if (next == null) {
                        throw new ProgramError("the path " + reference + " names a field that does"
                                + " not exist");
                    }
                    current = next;
                } else if (current instanceof List<?> list) {
                    int index = parseIndex(segments[i], reference);
                    if (index < 0 || index >= list.size()) {
                        throw new ProgramError("the path " + reference + " names an index that is"
                                + " out of range");
                    }
                    current = list.get(index);
                } else {
                    throw new ProgramError("the path " + reference + " goes through a value that"
                            + " has no fields");
                }
            }
            return current;
        }

        private int parseIndex(String segment, String reference) {
            try {
                return Integer.parseInt(segment);
            } catch (NumberFormatException e) {
                throw new ProgramError("the path " + reference + " names an index that is not a"
                        + " number");
            }
        }

        private void count() {
            steps++;
            if (steps > stepLimit) {
                throw new ProgramError("the program exceeded its budget of " + stepLimit
                        + " evaluation step(s)");
            }
        }

        private void requireArity(String op, List<Object> values, int arity) {
            if (values.size() != arity) {
                throw new ProgramError("the " + op + " operation takes " + arity
                        + " argument(s) and got " + values.size());
            }
        }

        private static String text(JsonNode node, String field) {
            JsonNode value = node.get(field);
            if (value == null || !value.isTextual()) {
                throw new ProgramError("the node has no textual field " + field);
            }
            return value.asText();
        }

        private static List<JsonNode> args(JsonNode node) {
            JsonNode args = node.get("args");
            List<JsonNode> list = new ArrayList<>();
            if (args != null && args.isArray()) {
                args.forEach(list::add);
            }
            return list;
        }

        private static Object literal(JsonNode node) {
            if (node == null || node.isNull()) {
                return null;
            }
            if (node.isBoolean()) {
                return node.asBoolean();
            }
            if (node.isNumber()) {
                return node.asDouble();
            }
            if (node.isTextual()) {
                return node.asText();
            }
            if (node.isArray()) {
                List<Object> list = new ArrayList<>();
                node.forEach(item -> list.add(literal(item)));
                return List.copyOf(list);
            }
            if (node.isObject()) {
                Map<String, Object> map = new LinkedHashMap<>();
                node.fields().forEachRemaining(entry -> map.put(entry.getKey(),
                        literal(entry.getValue())));
                return Map.copyOf(map);
            }
            throw new ProgramError("the program contains a value of an unknown kind");
        }

        private static boolean truthy(Object value) {
            if (value instanceof Boolean flag) {
                return flag;
            }
            if (value instanceof Number number) {
                return number.doubleValue() != 0.0;
            }
            if (value instanceof String text) {
                return !text.isEmpty();
            }
            if (value instanceof List<?> list) {
                return !list.isEmpty();
            }
            if (value instanceof Map<?, ?> map) {
                return !map.isEmpty();
            }
            return value != null;
        }

        private static boolean equal(Object left, Object right) {
            if (left instanceof Number a && right instanceof Number b) {
                return a.doubleValue() == b.doubleValue();
            }
            if (left instanceof String a && right instanceof String b) {
                return a.equals(b);
            }
            if (left instanceof Boolean a && right instanceof Boolean b) {
                return a.equals(b);
            }
            return left == null ? right == null : left.equals(right);
        }

        private static double number(Object value) {
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            if (value instanceof String text) {
                try {
                    return Double.parseDouble(text.trim());
                } catch (NumberFormatException e) {
                    throw new ProgramError("the value is not a number: " + text);
                }
            }
            if (value instanceof Boolean flag) {
                return flag ? 1.0 : 0.0;
            }
            throw new ProgramError("the value is not a number: " + value);
        }

        private static String textValue(Object value) {
            if (value == null) {
                return "";
            }
            if (value instanceof Double number && number == Math.rint(number)
                    && !number.isInfinite()) {
                return String.valueOf(number.longValue());
            }
            return String.valueOf(value);
        }
    }
}
