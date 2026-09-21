# Multi-modal Learning — Implementation Plan

**Status:** 🔧 PROTOTYPE (MultimodalTrainer exists)
**Priority:** MEDIUM
**Estimated effort:** 4-6 weeks
**Target:** v3.60

---

## Problem Statement

Current MultimodalTrainer only classifies files. Need:
1. Real multi-modal feature extraction
2. Cross-modal alignment
3. Unified representation learning
4. Multi-modal inference

---

## Implementation Steps

### Step 1: Feature Extractors (Week 1-2)
```java
// matrix-core/src/main/java/io/matrix/multimodal/FeatureExtractor.java
public interface FeatureExtractor {
    float[] extract(Object input);
    String modality();
}

// Text feature extractor
@ApplicationScoped
public class TextFeatureExtractor implements FeatureExtractor {
    @Override
    public float[] extract(Object input) {
        String text = (String) input;
        // Use BooleanRag to convert text to boolean features
        BooleanIndex index = BooleanIndex.fromText(text);
        return index.toFeatureVector();
    }
}

// Image feature extractor
@ApplicationScoped
public class ImageFeatureExtractor implements FeatureExtractor {
    @Override
    public float[] extract(Object input) {
        byte[] imageData = (byte[]) input;
        // Extract features using VQ-VAE proxy
        return vqvaeProxy.encode(imageData);
    }
}

// Audio feature extractor
@ApplicationScoped
public class AudioFeatureExtractor implements FeatureExtractor {
    @Override
    public float[] extract(Object input) {
        byte[] audioData = (byte[]) input;
        // Extract MFCC features
        return mfccExtractor.extract(audioData);
    }
}
```

### Step 2: Cross-Modal Alignment (Week 2-3)
```java
// matrix-core/src/main/java/io/matrix/multimodal/CrossModalAligner.java
@ApplicationScoped
public class CrossModalAligner {
    
    private final Map<String, FeatureExtractor> extractors;
    private final float[][] alignmentMatrix;
    
    public CrossModalAligner() {
        this.extractors = Map.of(
            "text", new TextFeatureExtractor(),
            "image", new ImageFeatureExtractor(),
            "audio", new AudioFeatureExtractor()
        );
        this.alignmentMatrix = loadAlignmentMatrix();
    }
    
    public float[] align(String sourceModality, String targetModality, float[] features) {
        // Project features from source to target modality space
        int sourceIdx = getModalityIndex(sourceModality);
        int targetIdx = getModalityIndex(targetModality);
        
        float[] aligned = new float[features.length];
        for (int i = 0; i < features.length; i++) {
            aligned[i] = dotProduct(features, alignmentMatrix[sourceIdx * 3 + targetIdx]);
        }
        return aligned;
    }
}
```

### Step 3: Unified Representation (Week 3-4)
```java
// matrix-core/src/main/java/io/matrix/multimodal/UnifiedRepresentation.java
@ApplicationScoped
public class UnifiedRepresentation {
    
    @Inject
    CrossModalAligner aligner;
    
    public TruthTable toTruthTable(Object input, String modality) {
        // Extract features
        float[] features = aligner.extract(modality, input);
        
        // Quantize to boolean
        boolean[] bits = quantize(features);
        
        // Create TruthTable
        return TruthTable.fromBits(bits);
    }
    
    public Object fromTruthTable(TruthTable tt, String targetModality) {
        // Convert TruthTable to features
        float[] features = tt.toFeatureVector();
        
        // Align to target modality
        float[] aligned = aligner.align("unified", targetModality, features);
        
        // Decode
        return decode(aligned, targetModality);
    }
    
    private boolean[] quantize(float[] features) {
        boolean[] bits = new boolean[features.length];
        for (int i = 0; i < features.length; i++) {
            bits[i] = features[i] > 0.5f;
        }
        return bits;
    }
}
```

### Step 4: Multi-Modal Training (Week 4-5)
```java
// matrix-core/src/main/java/io/matrix/multimodal/MultimodalTrainer.java
@ApplicationScoped
public class MultimodalTrainer {
    
    @Inject
    UnifiedRepresentation unified;
    
    @Inject
    AgentBrainService brainService;
    
    public void train(Map<String, Object> inputs) {
        // inputs: {"text": "...", "image": bytes, "audio": bytes}
        
        // Create unified representation
        TruthTable[] tables = inputs.entrySet().stream()
            .map(e -> unified.toTruthTable(e.getValue(), e.getKey()))
            .toArray(TruthTable[]::new);
        
        // Merge into single representation
        TruthTable merged = merge(tables);
        
        // Train brain
        brainService.train(merged);
    }
    
    private TruthTable merge(TruthTable[] tables) {
        // XOR-merge all modalities
        boolean[] merged = tables[0].toBits();
        for (int i = 1; i < tables.length; i++) {
            boolean[] bits = tables[i].toBits();
            for (int j = 0; j < merged.length; j++) {
                merged[j] ^= bits[j];
            }
        }
        return TruthTable.fromBits(merged);
    }
}
```

### Step 5: Multi-Modal Inference (Week 5)
```java
// matrix-core/src/main/java/io/matrix/multimodal/MultimodalInference.java
@ApplicationScoped
public class MultimodalInference {
    
    @Inject
    UnifiedRepresentation unified;
    
    @Inject
    AgentBrainService brainService;
    
    public Map<String, Object> infer(Map<String, Object> inputs) {
        // Create unified representation
        TruthTable[] tables = inputs.entrySet().stream()
            .map(e -> unified.toTruthTable(e.getValue(), e.getKey()))
            .toArray(TruthTable[]::new);
        
        TruthTable merged = merge(tables);
        
        // Infer
        TruthTable result = brainService.infer(merged);
        
        // Decode to all modalities
        Map<String, Object> outputs = new HashMap<>();
        for (String modality : inputs.keySet()) {
            outputs.put(modality, unified.fromTruthTable(result, modality));
        }
        return outputs;
    }
}
```

### Step 6: Integration Tests (Week 6)
```java
@QuarkusTest
class MultimodalTest {
    
    @Inject
    MultimodalTrainer trainer;
    
    @Inject
    MultimodalInference inference;
    
    @Test
    void testTextImageTraining() {
        Map<String, Object> inputs = Map.of(
            "text", "Hello world",
            "image", loadTestImage()
        );
        
        trainer.train(inputs);
        
        // Verify inference
        Map<String, Object> outputs = inference.infer(inputs);
        assertNotNull(outputs.get("text"));
        assertNotNull(outputs.get("image"));
    }
}
```

---

## Supported Modalities

| Modality | Input | Feature Extraction |
|----------|-------|-------------------|
| Text | String | BooleanRag |
| Image | byte[] | VQ-VAE |
| Audio | byte[] | MFCC |
| Video | byte[] | Frame sampling |

---

## Verification

```bash
# Run multi-modal tests
./gradlew :matrix-core:test --tests "*MultimodalTest"

# Test with sample data
curl -X POST http://localhost:9091/api/v1/multimodal/train \
  -F "text=Hello world" \
  -F "image=@test.png"
```
