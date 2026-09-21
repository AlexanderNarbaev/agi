package io.matrix.noosphere;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreditModelTest {

    private CreditModel model;

    @BeforeEach
    void setup() {
        model = new CreditModel();
    }

    @Test
    void shouldStartWithZeroCredits() {
        var credits = model.getCredits("instance-1");

        assertThat(credits.balance()).isEqualTo(0);
        assertThat(credits.publications()).isEqualTo(0);
    }

    @Test
    void shouldAwardPublicationCredits() {
        FnlPackage fnl = FnlPackage.builder()
                .name("Test").authorInstanceId("instance-1").accuracy(0.9).build();

        double reward = model.awardPublication("instance-1", fnl);

        assertThat(reward).isGreaterThan(0);
        assertThat(model.getCredits("instance-1").publications()).isEqualTo(1);
    }

    @Test
    void shouldAwardBonusForHighAccuracy() {
        FnlPackage high = FnlPackage.builder()
                .name("High").authorInstanceId("i1").accuracy(0.95).build();
        FnlPackage low = FnlPackage.builder()
                .name("Low").authorInstanceId("i2").accuracy(0.5).build();

        double highReward = model.awardPublication("i1", high);
        double lowReward = model.awardPublication("i2", low);

        assertThat(highReward).isGreaterThan(lowReward);
    }

    @Test
    void shouldChargeDownloadCost() {
        FnlPackage fnl = FnlPackage.builder()
                .name("Test").authorInstanceId("i1").build();
        model.awardPublication("i1", fnl);

        boolean allowed = model.chargeDownload("i1");
        assertThat(allowed).isTrue();
    }

    @Test
    void shouldRejectDownloadWithoutCredits() {
        boolean allowed = model.chargeDownload("poor-instance");

        assertThat(allowed).isFalse();
    }

    @Test
    void shouldCheckAffordability() {
        FnlPackage fnl = FnlPackage.builder()
                .name("Test").authorInstanceId("i1").build();
        model.awardPublication("i1", fnl);

        assertThat(model.canAfford("i1", 5.0)).isTrue();
        assertThat(model.canAfford("i1", 100.0)).isFalse();
    }

    @Test
    void shouldRankByReputation() {
        for (int i = 0; i < 5; i++) {
            model.awardPublication("instance-" + i, FnlPackage.builder()
                    .name("F" + i).authorInstanceId("instance-" + i).build());
        }

        var top = model.topReputation(3);
        assertThat(top).hasSize(3);
        assertThat(top.get(0).reputation()).isGreaterThanOrEqualTo(top.get(1).reputation());
    }
}
