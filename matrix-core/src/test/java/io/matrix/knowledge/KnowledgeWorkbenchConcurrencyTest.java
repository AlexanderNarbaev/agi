package io.matrix.knowledge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CONCEPT-TO-ACTION — the workbench's asking path under concurrency.
 *
 * <p>The HTTP surface serves every request through ONE {@code @ApplicationScoped}
 * workbench, so two requests can arrive at the same time. The store holds ONE database
 * connection, and the conversation map is shared: the asking path therefore has to be
 * serialized, and it has to be serialized in ONE order.</p>
 *
 * <p>These tests pin that order. The deadlock they guard against is an ABBA: the asking
 * thread holds the workbench's monitor and waits for the conversation map's lock, while
 * the dialogue thread holds the map's lock and waits for the monitor. It is invisible to a
 * single-threaded test, and it hangs the request threads forever when it happens.</p>
 */
class KnowledgeWorkbenchConcurrencyTest {

    @TempDir
    Path temporary;

    private KnowledgeWorkbench workbench;

    @BeforeEach
    void setUp() throws Exception {
        workbench = KnowledgeWorkbench.at(temporary.resolve("workbench.sqlite").toString());
        workbench.loadBundledPackage(KnowledgeWorkbench.DEFAULT_WORKSPACE);
    }

    @Test
    @DisplayName("two concurrent questions are both answered")
    void twoConcurrentQuestions() throws Exception {
        List<String> answers = new ArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int racer = 0; racer < 2; racer++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return workbench.ask(KnowledgeWorkbench.DEFAULT_WORKSPACE, "What is photosynthesis?",
                            AbstractionLevel.INTRODUCTORY).statement();
                }));
            }
            start.countDown();
            for (Future<String> future : futures) {
                answers.add(future.get(120, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(answers).hasSize(2);
        assertThat(answers).allSatisfy(answer -> assertThat(answer).isNotBlank());
    }

    @Test
    @DisplayName("a question and a dialogue turn do not deadlock")
    void askingAndTalkingDoNotDeadlock() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Answer> asking = pool.submit(() -> {
                start.await();
                return workbench.ask(KnowledgeWorkbench.DEFAULT_WORKSPACE, "What is photosynthesis?",
                        AbstractionLevel.INTRODUCTORY);
            });
            Future<Object> talking = pool.submit(() -> {
                start.await();
                return workbench.context(KnowledgeWorkbench.DEFAULT_WORKSPACE);
            });
            start.countDown();
            // both must finish: a deadlock would leave them waiting for ever, and the
            // timeout is what turns a deadlock into a failed test instead of a hung build
            assertThat(asking.get(120, TimeUnit.SECONDS)).isNotNull();
            assertThat(talking.get(120, TimeUnit.SECONDS)).isNotNull();
        } finally {
            pool.shutdownNow();
        }
    }
}
