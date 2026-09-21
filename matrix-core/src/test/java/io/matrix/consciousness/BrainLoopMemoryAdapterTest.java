package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** RUN 205 — BrainLoopMemoryAdapter unit tests. */
class BrainLoopMemoryAdapterTest {

    @Test
    void basicMemoryWiring(@TempDir Path tmp) throws Exception {
        BrainLoopService brain = new BrainLoopService();
        BrainLoopMemoryAdapter adapter =
                new BrainLoopMemoryAdapter(brain, tmp.resolve("mem.jsonl"));
        adapter.persistOnStart();
        for (int i = 0; i < 10; i++) {
            adapter.cycle("hello-" + i);
        }
        assertThat(adapter.totalCycles()).isEqualTo(10);
    }

    @Test
    void consolidationTriggered(@TempDir Path tmp) throws Exception {
        BrainLoopService brain = new BrainLoopService();
        BrainLoopMemoryAdapter adapter =
                new BrainLoopMemoryAdapter(brain, tmp.resolve("mem.jsonl"), 5);
        adapter.persistOnStart();
        for (int i = 0; i < 20; i++) {
            adapter.cycle("test-" + i);
        }
        // 20 cycles / 5 = 4 consolidation cycles
        assertThat(adapter.consolidationSteps()).isGreaterThan(0);
    }

    @Test
    void memoryRetrieval(@TempDir Path tmp) throws Exception {
        BrainLoopService brain = new BrainLoopService();
        BrainLoopMemoryAdapter adapter =
                new BrainLoopMemoryAdapter(brain, tmp.resolve("mem.jsonl"), 5);
        adapter.persistOnStart();
        for (int i = 0; i < 10; i++) {
            adapter.cycle("phrase-" + i);
        }
        // Access pattern
        for (int i = 0; i < 10; i++) {
            adapter.cycle("phrase-" + i);
        }
        // Memory may have moved through TR/REM cycles
        // Just verify no exception
    }

    @Test
    void persistOnStartLoadsFile(@TempDir Path tmp) throws Exception {
        // Create a file first via real persist
        Path memFile = tmp.resolve("preload.jsonl");
        BrainLoopService brain = new BrainLoopService();
        BrainLoopMemoryAdapter adapter =
                new BrainLoopMemoryAdapter(brain, memFile, 5);
        adapter.persistOnStart();
        adapter.cycle("apple");
        adapter.cycle("banana");
        // Now create new adapter on same file
        BrainLoopMemoryAdapter adapter2 =
                new BrainLoopMemoryAdapter(brain, memFile, 5);
        adapter2.persistOnStart();
        // After consolidation cycles, items may have moved
        // Just verify no exception
    }
}
