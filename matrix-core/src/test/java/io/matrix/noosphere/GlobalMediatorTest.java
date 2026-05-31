package io.matrix.noosphere;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalMediatorTest {

    private GlobalMediator mediator;
    private NoosphereRegistry registry;
    private CreditModel creditModel;

    @BeforeEach
    void setup() {
        registry = new NoosphereRegistry();
        creditModel = new CreditModel();
        mediator = new GlobalMediator(registry, creditModel);

        creditModel.awardPublication("instance-1", FnlPackage.builder()
                .name("F1").authorInstanceId("instance-1").accuracy(0.9).build());
        creditModel.awardPublication("instance-2", FnlPackage.builder()
                .name("F2").authorInstanceId("instance-2").accuracy(0.85).build());
        creditModel.awardPublication("instance-3", FnlPackage.builder()
                .name("F3").authorInstanceId("instance-3").accuracy(0.8).build());
    }

    @Test
    void shouldElectCouncil() {
        mediator.electCouncil(3);

        assertThat(mediator.councilMembers()).hasSize(3);
    }

    @Test
    void shouldProposePublication() {
        mediator.electCouncil(3);

        FnlPackage fnl = FnlPackage.builder()
                .name("New FNL").authorInstanceId("i4")
                .type("TEXT").accuracy(0.92).build();

        var propId = mediator.proposePublication(fnl, "i4");

        assertThat(propId).isNotNull();
        assertThat(mediator.consensus().proposalCount()).isEqualTo(1);
    }

    @Test
    void shouldVoteAndDecide() {
        mediator.electCouncil(3);

        FnlPackage fnl = FnlPackage.builder()
                .name("New FNL").authorInstanceId("i4")
                .type("TEXT").accuracy(0.92).build();

        var propId = mediator.proposePublication(fnl, "i4");

        mediator.councilVote(propId, "instance-1", true);
        mediator.councilVote(propId, "instance-2", true);
        mediator.councilVote(propId, "instance-3", true);

        String decision = mediator.decide(propId);
        assertThat(decision).isEqualTo("APPROVED");
    }

    @Test
    void shouldRejectWithTooFewVotes() {
        mediator.electCouncil(3);

        FnlPackage fnl = FnlPackage.builder()
                .name("Controversial").authorInstanceId("i4")
                .type("TEXT").accuracy(0.5).build();

        var propId = mediator.proposePublication(fnl, "i4");

        mediator.councilVote(propId, "instance-1", false);
        mediator.councilVote(propId, "instance-2", false);

        String decision = mediator.decide(propId);
        assertThat(decision).isEqualTo("REJECTED");
    }

    @Test
    void shouldLogActions() {
        mediator.electCouncil(3);
        FnlPackage fnl = FnlPackage.builder()
                .name("F").authorInstanceId("i4").build();
        mediator.proposePublication(fnl, "i4");

        assertThat(mediator.actionLog()).isNotEmpty();
    }
}
