# MATRIX IMPLEMENTATION GUIDE
# Практические инструкции по реализации L0-L22

**Ветка:** docs/matrix-rebuild
**Дата:** 2026-08-08
**Статус:** Пошаговое руководство

---

## 1. Architecture verification (what's already working)

Run this to verify the current system:

```bash
# 1. Health check
curl http://localhost:30091/api/v1/health
# Expected: {"status":"UP","version":"2.1.0"}

# 2. Chat (corpus retrieval)
curl -X POST http://localhost:30091/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"M.A.T.R.I.X.","messages":[{"role":"user","content":"What is gravity?"}],"max_tokens":200}'
# Expected: JSON with real corpus content

# 3. Brain pipeline (3-block)
curl -X POST http://localhost:30091/v1/brain/think \
  -H "Content-Type: application/json" \
  -d '{"text":"What is gravity?"}'
# Expected: {"content":"...","latencyMicros":...,"executions":{...}}

# 4. Long-horizon planning
curl -X POST http://localhost:30091/v1/brain/plan \
  -H "Content-Type: application/json" \
  -d '{"text":"Build a neural network"}'
# Expected: {"stepCount":4,"steps":[...]}

# 5. Sub-agent tool use
curl -X POST http://localhost:30091/v1/brain/subagent \
  -H "Content-Type: application/json" \
  -d '{"task":"Compute (25+75)*3","tool":"calculator","toolArgs":"(25+75)*3"}'
# Expected: {"ok":true,"toolOutput":"300.0"}

# 6. Training
curl -X POST http://localhost:30091/api/v1/agent/train \
  -H "Content-Type: application/json" \
  -d '{"generations":20,"population":32,"k":5}'
# Expected: {"bestFitness":..., "generations":...}

# 7. Tools list
curl http://localhost:30091/api/v1/tools/list
# Expected: 8 tools
```

---

## 2. Fix the env blocker (jacoco coverage)

The coverage measurement is blocked because Quarkus's native-image plugin excludes the JaCoCo agent. Fix:

```gradle
// matrix-core/build.gradle
// Find the section that has the exclusion and add:
// (replace or merge with existing jacocoExcluded list)
def jacocoExcluded = [
    'io/matrix/MatrixApplication*', 'io/matrix/MatrixSimulation*',
    'io/matrix/SystemDemo*', 'io/matrix/MinecraftExperiment*',
    'io/matrix/MatrixTopCommand*', 'io/matrix/cli/**',
    'io/matrix/pilot/**', 'io/matrix/rag/**', 'io/matrix/compression/**',
    'io/matrix/explainability/**', 'io/matrix/nas/**', 'io/matrix/mcts/**',
    'io/matrix/vqvae/**', 'io/matrix/civilization/**', 'io/matrix/economy/**',
    'io/matrix/cauldron/**', 'io/matrix/hades/**', 'io/matrix/proxy/**',
    'io/matrix/shadow/**', 'io/matrix/snapshot/**', 'io/matrix/protocol/**',
    // DO NOT exclude the quarkus agent filter — we need it
    // Add a comment so nobody re-adds it:
    // "Quarkus native-image plugin filters jacoco agent — keep excluded for now"
]
```

**Alternative:** Use JaCoCo's `OfflineInstrumentTask` to instrument classes at build time, then run tests without the agent:

```gradle
task jacocoOfflineInstrument(type: JavaExec) {
    classpath = configurations.jacocoAgent
    mainClass = 'org.jacoco.agent.OfflineInstrumentTask'
    args = ['classesDir', 'instrumented']
}
```

Then modify the `test` task to use instrumented classes.

---

## 3. Sequential HF training (Wave 16)

Run the script with the already-downloaded HF models:

```bash
# Make executable
chmod +x sequential-train.sh

# Run on one model first (Qwen2.5-0.5B is smallest)
./sequential-train.sh 1  # index 1 = Qwen/Qwen2.5-0.5B
```

The script:
1. Loads from `~/.cache/huggingface/hub/models--Qwen--Qwen2.5-0.5B/snapshots/.../model.safetensors`
2. Copies to minikube `/data/models/cache/Qwen_Qwen2.5-0.5B/`
3. Triggers Quarkus `train-all` subcommand via the running pod
4. Extracts neurons to `/data/models/pretrained/qwen2.5-0.5b/`
5. Deletes the safetensors from minikube cache
6. Verifies the neuron files exist

**Expected output:**
```
Sequential HF trainer — starting at index 1
Plan: Qwen/Qwen2.5-0.5B Qwen/Qwen3-0.6B Qwen/Qwen2.5-1.5B Qwen/Qwen3-1.7B deepseek-ai/DeepSeek-R1-Distill-Qwen-1.5B
Constraints: max disk 10GB, neurons/layer=30, K=16
=== [1/5] Qwen/Qwen2.5-0.5B ===
Disk free: 48GB
Loading Qwen/Qwen2.5-0.5B from local cache -> /data/models/cache/Qwen_Qwen2.5-0.5B
Source: /home/alexandr-narbaev/.cache/huggingface/hub/models--Qwen--Qwen2.5-0.5B/snapshots/060db6499f32faf8b98477b0a26969ef7d8b9987/
total 12
drwxrwxr-x 2 docker 1000 4096 Jul  5 20:36 .
drwxr-xr-x 5 root   root 4096 Jul 27 14:09 ..
-rw-rw-r-- 1 docker 1000 979599245 Jul  5 20:36 model.safetensors
Extracting neurons from /data/models/cache/Qwen_Qwen2.5-0.5B -> /data/models/pretrained/qwen2.5-0.5b
[Neuron extraction output from Quarkus train-all]
DELETING original safetensors: /data/models/cache/Qwen_Qwen2.5-0.5B
Done. Processed 1 models.
```

---

## 4. Implement real tools (Wave 18 completion)

Current `calculator` and `datetime` work. The rest are stubs. Implement:

### web_search
```java
private String webSearch(String query) {
    try {
        // Use DuckDuckGo Instant Answers API
        String url = "https://api.duckduckgo.com/?q=" +
                URLEncoder.encode(query, StandardCharsets.UTF_8) +
                "&format=json&no_redirect=1";
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return parseSearchResults(response.body());
    } catch (Exception e) {
        return "web_search error: " + e.getMessage();
    }
}
```

### web_fetch
```java
private String webFetch(String url) {
    try {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", "Mozilla/5.0")
                .build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        // Strip HTML tags, extract text
        return stripHtml(response.body());
    } catch (Exception e) {
        return "web_fetch error: " + e.getMessage();
    }
}
```

### code_execute
```java
private String codeExecute(String code) {
    try {
        // Sandbox: no System.exit, no file I/O outside /tmp
        var script = new javax.script.ScriptEngineManager()
                .getEngineByName("JavaScript");
        if (script == null) {
            // Fallback: use ProcessBuilder with timeout
            ProcessBuilder pb = new ProcessBuilder("node", "-e", code);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            proc.waitFor(5, TimeUnit.SECONDS);
            return new String(proc.getInputStream().readAllBytes());
        }
        Object result = script.eval(code);
        return String.valueOf(result);
    } catch (Exception e) {
        return "code_execute error: " + e.getMessage();
    }
}
```

---

## 5. Wire tools into LongHorizonPlanner (Wave 18 → real execution)

Modify `LongHorizonPlanner.decompose()` to map steps to tools:

```java
private List<String> decompose(String goal) {
    var out = new ArrayList<String>();
    var g = goal == null ? "" : goal.toLowerCase();
    if (g.isBlank()) {
        out.add("clarify: ask for more context");
        out.add("verify: confirm intent");
    } else if (g.contains("research") || g.contains("find") || g.contains("search")) {
        out.add("analyze: " + goal);
        out.add("plan: design search strategy");
        out.add("execute: web_search('" + extractQuery(goal) + "')");
        out.add("verify: check that search returned results");
        out.add("synthesize: aggregate findings");
    } else if (g.contains("compute") || g.contains("calculate")) {
        out.add("analyze: " + goal);
        out.add("execute: calculator('" + extractExpression(goal) + "')");
        out.add("verify: check that result is numeric");
    } else {
        out.add("analyze: " + goal);
        out.add("plan: design approach");
        out.add("execute: reason via BrainPipeline");
        out.add("verify: confirm output quality");
    }
    return out;
}
```

Then in `plan()`, each step with a tool call actually invokes it.

---

## 6. Real multi-modal input (Wave 16 → real perception)

### Image processing
```java
// Add to DefaultBrainPipeline.java
private String processImage(byte[] imageData) {
    try {
        var img = Imaging.getBufferedImage(imageData);
        int w = img.getWidth(), h = img.getHeight();
        // Downsample to 8x8 grayscale → 64 floats
        float[] feats = new float[64];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int rgb = img.getRGB(x * w / 8, y * h / 8);
                int gray = ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
                feats[y * 8 + x] = gray / 3.0f / 255.0f;
            }
        }
        return "[image:" + Arrays.toString(feats) + "]";
    } catch (Exception e) {
        return "[image:error]";
    }
}
```

### Audio processing
```java
private String processAudio(byte[] audioData) {
    try {
        // WAV parser (RIFF header → PCM samples)
        var audio = AudioSystem.getAudioInputStream(
                new ByteArrayInputStream(audioData));
        var format = audio.getFormat();
        int channels = format.getChannels();
        int sampleRate = (int) format.getSampleRate();
        // Downsample to 8 frames × 8 channels = 64 floats
        float[] feats = new float[64];
        // ... extract samples ...
        return "[audio:" + Arrays.toString(feats) + "]";
    } catch (Exception e) {
        return "[audio:error]";
    }
}
```

Add Apache Commons Imaging + Java Sound API to `build.gradle`:

```gradle
implementation 'org.apache.commons:commons-imaging:1.0-alpha5'
```

---

## 7. Persistent world model (HierarchicalMemory backend)

Add SQLite via JDBC:

```java
// Add to build.gradle
implementation 'org.xerial:sqlite-jdbc:3.43.0.0'

// Modify HierarchicalMemory to use SQLite
public class HierarchicalMemory {
    private final Connection conn;
    
    public HierarchicalMemory() {
        try {
            this.conn = DriverManager.getConnection("jdbc:sqlite:/data/memory.sqlite");
            init();
        } catch (SQLException e) {
            throw new RuntimeException("Memory backend init failed", e);
        }
    }
    
    private void init() throws SQLException {
        try (var st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS memory (" +
                    "id TEXT PRIMARY KEY, " +
                    "level INTEGER, " +
                    "content TEXT, " +
                    "domain TEXT, " +
                    "tags TEXT, " +
                    "access_count INTEGER, " +
                    "last_accessed INTEGER, " +
                    "importance REAL)");
        }
    }
    
    public MemoryEntry store(Level level, String content, String domain, Set<String> tags) {
        // INSERT into sqlite
    }
    
    public List<MemoryEntry> search(String query, int limit) {
        // SELECT with LIKE or FTS
    }
}
```

---

## 8. Run full test suite with coverage

After fixing the Quarkus plugin exclusion:

```bash
cd matrix-core
./gradlew test jacocoTestReport --no-daemon
# Expected: BUILD SUCCESSFUL
# Report at: matrix-core/build/reports/jacoco/test/jacocoTestReport.xml
```

---

## 9. CI integration

Add to `.github/workflows/ci.yml`:

```yaml
jobs:
  coverage:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '25'
          distribution: 'temurin'
      - run: ./gradlew :matrix-core:test :matrix-core:jacocoTestReport --no-daemon
      - run: |
          echo "Coverage:"
          python3 -c "
          import xml.etree.ElementTree as ET
          tree = ET.parse('matrix-core/build/reports/jacoco/test/jacocoTestReport.xml')
          for counter in tree.iter('counter'):
              if counter.get('type') == 'METHOD':
                  missed = int(counter.get('missed'))
                  covered = int(counter.get('covered'))
                  total = missed + covered
                  pct = covered / total * 100
                  print(f'METHOD: {covered}/{total} = {pct:.1f}%')
                  if pct < 82:
                      exit(1)
          "
```

---

## 10. Deployment

```bash
# Local development
./gradlew :matrix-core:quarkusDev

# Docker
docker build -t matrix-core:3.59.3 -f Dockerfile .
docker save matrix-core:3.59.3 | docker exec -i minikube docker load

# K8s
kubectl -n matrix set image deployment/matrix-core matrix-core=matrix-core:3.59.3
kubectl -n matrix rollout status deployment/matrix-core

# Verify
curl http://localhost:30091/api/v1/health
```

---

**End of IMPLEMENTATION_GUIDE.md**
