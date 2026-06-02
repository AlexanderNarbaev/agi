package io.matrix.pilot;

import io.matrix.noosphere.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pilot #7: Noosphere — FNL exchange between instances")
class NoospherePilotTest {

    private NoosphereRegistry registry;
    private KnowledgeIndex index;

    @BeforeEach
    void setUp() {
        registry = new NoosphereRegistry();
        index = new KnowledgeIndex(registry);
    }

    @Test
    @DisplayName("Should publish an FNL in the Noosphere")
    void should_publishFnl() {
        FnlPackage pkg = FnlPackage.builder()
                .name("test_vision_fnl")
                .type("VISION")
                .version("1.0.0")
                .authorInstanceId("instance-001")
                .accuracy(0.95)
                .generation(50)
                .description("Vision FNL for object detection")
                .tags("vision", "detection")
                .certified(false)
                .build();

        var result = registry.publish(pkg);

        assertThat(result.success()).isTrue();
        assertThat(result.entryId()).isNotNull();
    }

    @Test
    @DisplayName("Should find FNLs by type")
    void should_findByType() {
        FnlPackage pkg1 = FnlPackage.builder()
                .name("vision_basic").type("VISION").version("1.0.0")
                .authorInstanceId("i1").accuracy(0.9).generation(10)
                .description("Basic vision").tags("vision").certified(false).build();

        FnlPackage pkg2 = FnlPackage.builder()
                .name("vision_advanced").type("VISION").version("1.0.0")
                .authorInstanceId("i2").accuracy(0.95).generation(100)
                .description("Advanced vision").tags("vision", "detection").certified(true).build();

        var r1 = registry.publish(pkg1);
        var r2 = registry.publish(pkg2);

        index.index(r1.entryId(), pkg1);
        index.index(r2.entryId(), pkg2);

        var results = registry.byType("VISION");
        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("Should search FNLs by keyword")
    void should_searchByKeyword() {
        FnlPackage pkg = FnlPackage.builder()
                .name("nlp_classifier").type("NLP").version("1.0.0")
                .authorInstanceId("i1").accuracy(0.85).generation(50)
                .description("NLP classifier for sentiment analysis")
                .tags("nlp", "sentiment").certified(false).build();

        var r = registry.publish(pkg);
        index.index(r.entryId(), pkg);

        var results = index.search("sentiment");
        assertThat(results).isNotEmpty();
        assertThat(results.get(0).fnl().name()).isEqualTo("nlp_classifier");
    }

    @Test
    @DisplayName("Should award credits for FNL publication")
    void should_awardCredits() {
        CreditModel credit = new CreditModel();

        FnlPackage pkg = FnlPackage.builder()
                .name("nav_pro").type("NAVIGATION").version("1.0.0")
                .authorInstanceId("i1").accuracy(0.98).generation(200)
                .description("Pro navigation FNL").tags("navigation").certified(true).build();

        double reward = credit.awardPublication("i1", pkg);
        assertThat(reward).isGreaterThan(10.0);

        var credits = credit.getCredits("i1");
        assertThat(credits.balance()).isEqualTo(reward);
        assertThat(credits.publications()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should prevent download without credits")
    void should_blockDownloadWithoutCredits() {
        CreditModel credit = new CreditModel();

        assertThat(credit.chargeDownload("poor-instance")).isFalse();
    }

    @Test
    @DisplayName("Should allow download with sufficient credits")
    void should_allowDownloadWithCredits() {
        CreditModel credit = new CreditModel();

        FnlPackage pkg = FnlPackage.builder()
                .name("test").type("TEST").version("1.0.0")
                .authorInstanceId("rich-instance").accuracy(1.0).generation(1)
                .description("Test").tags("test").certified(false).build();

        credit.awardPublication("rich-instance", pkg);

        assertThat(credit.chargeDownload("rich-instance")).isTrue();
    }

    @Test
    @DisplayName("Should rank by reputation")
    void should_rankByReputation() {
        CreditModel credit = new CreditModel();

        for (int i = 0; i < 5; i++) {
            FnlPackage pkg = FnlPackage.builder()
                    .name("fnl_" + i).type("TEST").version("1.0.0")
                    .authorInstanceId("instance-" + i).accuracy(0.8).generation(10)
                    .description("FNL " + i).tags("test").certified(false).build();
            credit.awardPublication("instance-" + i, pkg);
        }

        var top = credit.topReputation(3);
        assertThat(top).hasSize(3);
    }
}
