package io.matrix.pilot;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;

public class PyBulletBridge implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Process process;
    private final BufferedWriter writer;
    private final BufferedReader reader;

    public PyBulletBridge(String pythonScript) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "python3", pythonScript, "--json");
        pb.redirectErrorStream(true);
        this.process = pb.start();
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> train(int population, int generations, int k)
            throws IOException {
        var cmd = Map.of(
                "cmd", "train",
                "population", population,
                "generations", generations,
                "k", k);
        writer.write(MAPPER.writeValueAsString(cmd));
        writer.newLine();
        writer.flush();
        String response = reader.readLine();
        return MAPPER.readValue(response, Map.class);
    }

    @SuppressWarnings("unchecked")
    public double evaluate(Map<String, Object> neuron, int k) throws IOException {
        var cmd = Map.of(
                "cmd", "evaluate",
                "neuron", Map.of("k", k, "table", neuron.get("table")));
        writer.write(MAPPER.writeValueAsString(cmd));
        writer.newLine();
        writer.flush();
        String response = reader.readLine();
        Map<String, Object> result = MAPPER.readValue(response, Map.class);
        Number fitness = (Number) result.get("fitness");
        return fitness.doubleValue();
    }

    @Override
    public void close() throws IOException {
        try {
            writer.write("{\"cmd\":\"quit\"}\n");
            writer.flush();
        } catch (IOException ignored) {
        }
        writer.close();
        reader.close();
        process.destroyForcibly();
    }
}
