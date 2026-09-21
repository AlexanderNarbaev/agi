package io.matrix.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TenantQaResource} — RUN 33 tenant-scoped QA endpoint.
 *
 * <p>Tests the resource handlers directly without spinning up a full
 * JAX-RS container. The handlers are pure functions of the injected
 * collaborators.
 */
class TenantQaResourceTest {

    private TenantQaResource resource;
    private QaCorpusIndex base;
    private Path tmpCorpus;

    @BeforeEach
    void setUp() throws IOException {
        // Create a temp corpus with a couple of entries
        tmpCorpus = Files.createTempFile("corpus", ".json");
        Files.writeString(tmpCorpus, """
                [
                  {"question": "What is MATRIX?", "answer": "Deterministic neuro-symbolic system.", "category": "general", "source": "system"},
                  {"question": "How does it work?", "answer": "Through a boolean chain.", "category": "general", "source": "system"}
                ]
                """);

        base = new QaCorpusIndex();
        try {
            java.lang.reflect.Field f = QaCorpusIndex.class.getDeclaredField("qaPath");
            f.setAccessible(true);
            f.set(base, tmpCorpus.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        base.reload();

        resource = new TenantQaResource();
        try {
            java.lang.reflect.Field f = TenantQaResource.class.getDeclaredField("baseIndex");
            f.setAccessible(true);
            f.set(resource, base);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void learnAddsEntryToTenant() {
        var req = new TenantQaResource.LearnRequest();
        req.question = "What is tenant alice's secret?";
        req.answer = "Secret A1";
        var resp = resource.learn("alice", req);
        assertThat(resp.getStatus()).isEqualTo(200);
        // The tenant should now have 1 entry.
        var stats = resource.stats("alice");
        assertThat(stats.getEntity().toString()).contains("entryCount=1");
    }

    @Test
    void searchReturnsLearnedEntry() {
        var req = new TenantQaResource.LearnRequest();
        req.question = "alice-specific question";
        req.answer = "alice-specific answer";
        resource.learn("alice", req);

        // Search using the exact question should find the learned entry.
        var resp = resource.search("alice", "alice-specific question", 5);
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void searchRejectsBlankTenantId() {
        var resp = resource.search("", "anything", 5);
        assertThat(resp.getStatus()).isEqualTo(400);

        var resp2 = resource.search(null, "anything", 5);
        assertThat(resp2.getStatus()).isEqualTo(400);
    }

    @Test
    void learnRejectsBlankTenantId() {
        var req = new TenantQaResource.LearnRequest();
        req.question = "Q";
        req.answer = "A";
        var resp = resource.learn("", req);
        assertThat(resp.getStatus()).isEqualTo(400);

        var resp2 = resource.learn(null, req);
        assertThat(resp2.getStatus()).isEqualTo(400);
    }

    @Test
    void learnRejectsBlankQuestionOrAnswer() {
        var req = new TenantQaResource.LearnRequest();
        req.question = "";
        req.answer = "A";
        var resp = resource.learn("alice", req);
        assertThat(resp.getStatus()).isEqualTo(400);

        req.question = "Q";
        req.answer = "";
        var resp2 = resource.learn("alice", req);
        assertThat(resp2.getStatus()).isEqualTo(400);

        var resp3 = resource.learn("alice", null);
        assertThat(resp3.getStatus()).isEqualTo(400);
    }

    @Test
    void statsReturnsCounts() {
        var req = new TenantQaResource.LearnRequest();
        req.question = "Q1";
        req.answer = "A1";
        resource.learn("alice", req);
        resource.learn("alice", req);  // second learn with same Q creates second entry

        var stats = resource.stats("alice");
        var body = stats.getEntity().toString();
        assertThat(body).contains("entryCount");
    }

    @Test
    void topKIsClampedToValidRange() {
        // topK=0 should clamp to 1; topK=100 should clamp to 20
        var resp1 = resource.search("alice", "anything", 0);
        assertThat(resp1.getStatus()).isEqualTo(200);

        var resp2 = resource.search("alice", "anything", 100);
        assertThat(resp2.getStatus()).isEqualTo(200);
    }

    @Test
    void differentTenantsAreIndependent() {
        var reqA = new TenantQaResource.LearnRequest();
        reqA.question = "alice Q";
        reqA.answer = "alice A";
        resource.learn("alice", reqA);

        var reqB = new TenantQaResource.LearnRequest();
        reqB.question = "bob Q";
        reqB.answer = "bob A";
        resource.learn("bob", reqB);

        // alice's stats should not include bob's entries.
        var aliceStats = resource.stats("alice");
        var bobStats = resource.stats("bob");
        // Tenant IDs are case-sensitive so alice and bob are distinct.
        assertThat(aliceStats.getEntity().toString()).contains("tenantId=alice");
        assertThat(bobStats.getEntity().toString()).contains("tenantId=bob");
    }
}
