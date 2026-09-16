package io.matrix.consciousness;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PhiArchetypeDetectorTest {

    @Test
    void emptyProfilesReturnsEmptyArchetypes() {
        assertThat(PhiArchetypeDetector.findArchetypes(new ArrayList<>(), 4)).isEmpty();
    }

    @Test
    void singleProfileReturnsOneArchetype() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        profiles.add(makeProfile(0.5));
        List<PhiArchetypeDetector.Archetype> archetypes =
            PhiArchetypeDetector.findArchetypes(profiles, 4);
        assertThat(archetypes).hasSize(1);
        assertThat(archetypes.get(0).count()).isEqualTo(1);
    }

    @Test
    void identicalProfilesGroupIntoOneArchetype() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 5; i++) profiles.add(makeProfile(0.5));
        List<PhiArchetypeDetector.Archetype> archetypes =
            PhiArchetypeDetector.findArchetypes(profiles, 4);
        assertThat(archetypes).hasSize(1);
        assertThat(archetypes.get(0).count()).isEqualTo(5);
    }

    @Test
    void differentProfilesProduceDifferentArchetypes() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 4; i++) profiles.add(makeProfile(i / 4.0));
        List<PhiArchetypeDetector.Archetype> archetypes =
            PhiArchetypeDetector.findArchetypes(profiles, 4);
        // At least 2 archetypes (since values 0.0, 0.25, 0.5, 0.75 will be in different bins)
        assertThat(archetypes.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void topArchetypesReturnsSubset() {
        List<CognitiveGenesisProfile> profiles = new ArrayList<>();
        for (int i = 0; i < 10; i++) profiles.add(makeProfile(i / 10.0));
        List<PhiArchetypeDetector.Archetype> top =
            PhiArchetypeDetector.topArchetypes(profiles, 4, 2);
        assertThat(top.size()).isLessThanOrEqualTo(2);
    }

    @Test
    void signatureLengthIs13() {
        CognitiveGenesisProfile p = makeProfile(0.5);
        int[] sig = PhiArchetypeDetector.signature(p, 4);
        assertThat(sig.length).isEqualTo(13);
    }

    @Test
    void binReturnsValidIndex() {
        assertThat(PhiArchetypeDetector.bin(0.0, 4)).isEqualTo(0);
        assertThat(PhiArchetypeDetector.bin(0.5, 4)).isBetween(0, 3);
        assertThat(PhiArchetypeDetector.bin(1.0, 4)).isEqualTo(3);
        assertThat(PhiArchetypeDetector.bin(1.5, 4)).isEqualTo(3);  // clamped
    }

    private static CognitiveGenesisProfile makeProfile(double phi) {
        return new CognitiveGenesisProfile(
            phi, phi, phi, phi, phi, 0.5, phi,
            50.0, 0.5, 0.5, 2, 0.5, 2.0
        );
    }
}
