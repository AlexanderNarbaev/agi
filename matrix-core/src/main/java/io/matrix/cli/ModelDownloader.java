package io.matrix.cli;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * W441 — Model Downloader CLI.
 * 
 * Downloads ONNX model files from HuggingFace Hub or a direct URL.
 * 
 * Usage:
 *   java io.matrix.cli.ModelDownloader <model-id> [output-dir]
 *   java io.matrix.cli.ModelDownloader <direct-url> <output-file>
 * 
 * Default model: Qwen2.5-0.5B-Instruct (smaller, faster)
 */
public final class ModelDownloader {
    
    private static final String DEFAULT_MODEL_ID = "Qwen/Qwen2.5-0.5B-Instruct";
    private static final String DEFAULT_OUTPUT_DIR = "models/onnx/qwen05b";
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            printUsage();
            return;
        }
        
        if ("--list".equals(args[0])) {
            listModels();
            return;
        }
        
        String firstArg = args[0];
        if (firstArg.startsWith("http://") || firstArg.startsWith("https://")) {
            // Direct URL download
            String outputFile = args.length > 1 ? args[1] : "model.onnx";
            downloadFromUrl(firstArg, outputFile);
        } else {
            // HuggingFace model ID
            String modelId = firstArg;
            String outputDir = args.length > 1 ? args[1] : DEFAULT_OUTPUT_DIR;
            downloadFromHuggingFace(modelId, outputDir);
        }
    }
    
    private static void printUsage() {
        System.out.println("Model Downloader");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  ModelDownloader <model-id> [output-dir]   # Download from HF");
        System.out.println("  ModelDownloader <url> <output-file>       # Direct URL");
        System.out.println("  ModelDownloader --list                    # List known models");
        System.out.println();
        System.out.println("Default model: " + DEFAULT_MODEL_ID);
        System.out.println("Default output: " + DEFAULT_OUTPUT_DIR);
    }
    
    private static void listModels() {
        System.out.println("Known models for MATRIX:");
        System.out.println();
        System.out.println("  " + DEFAULT_MODEL_ID);
        System.out.println("    - Smallest, fastest, ~500MB");
        System.out.println("    - Good for testing and quick experiments");
        System.out.println();
        System.out.println("  Qwen/Qwen2-1.5B-Instruct");
        System.out.println("    - Better quality, ~1.5GB");
        System.out.println();
        System.out.println("  microsoft/Phi-3-mini-4k-instruct-onnx");
        System.out.println("    - Good for complex reasoning, ~2.3GB");
    }
    
    private static void downloadFromHuggingFace(String modelId, String outputDir) throws IOException {
        System.out.println("Downloading model: " + modelId);
        System.out.println("Output dir: " + outputDir);
        
        Path dir = Paths.get(outputDir);
        Files.createDirectories(dir);
        
        // List of required files for an ONNX model
        String[] requiredFiles = {
            "config.json",
            "tokenizer.json",
            "tokenizer_config.json",
            "special_tokens_map.json"
        };
        
        for (String file : requiredFiles) {
            String url = "https://huggingface.co/" + modelId + "/resolve/main/" + file;
            Path target = dir.resolve(file);
            System.out.println("  " + file + "...");
            try {
                downloadFromUrl(url, target.toString());
            } catch (IOException e) {
                System.out.println("    [skip: " + e.getMessage() + "]");
            }
        }
        
        // Try to download ONNX model file
        String[] onnxFiles = {"onnx/model.onnx", "onnx/model_quantized.onnx", "model.onnx"};
        for (String path : onnxFiles) {
            String url = "https://huggingface.co/" + modelId + "/resolve/main/" + path;
            try {
                String fileName = Paths.get(path).getFileName().toString();
                System.out.println("  onnx model (" + fileName + ")...");
                downloadFromUrl(url, dir.resolve("model.onnx").toString());
                break;  // Use first one that works
            } catch (IOException e) {
                // try next
            }
        }
        
        System.out.println();
        System.out.println("Download complete. Verify with:");
        System.out.println("  ./matrix-conv.sh test");
    }
    
    private static void downloadFromUrl(String urlStr, String outputPath) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "MATRIX-ModelDownloader/1.0");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);
        
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            conn.disconnect();
            throw new IOException("HTTP " + responseCode);
        }
        
        long contentLength = conn.getContentLengthLong();
        Path target = Paths.get(outputPath);
        Files.createDirectories(target.getParent() != null ? target.getParent() : Paths.get("."));
        
        try (InputStream in = conn.getInputStream()) {
            if (contentLength > 0) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("    OK (" + (contentLength / 1024) + " KB)");
            } else {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("    OK (size unknown)");
            }
        }
        
        conn.disconnect();
    }
}
