package io.matrix.neuron;

import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HierarchicalBrainTest {

    private final Random rng = new Random(42);

    @Test
    void shouldCreateBrainWithCorrectDimensions() {
        HierarchicalBrain brain = new HierarchicalBrain(rng);

        assertThat(brain.sensorLayer().outputWidth()).isEqualTo(12);
        assertThat(brain.sensorLayer().k()).isEqualTo(12);

        assertThat(brain.featureLayer().outputWidth()).isEqualTo(8);
        assertThat(brain.featureLayer().k()).isEqualTo(12);

        assertThat(brain.actionLayer().outputWidth()).isEqualTo(5);
        assertThat(brain.actionLayer().k()).isEqualTo(8);
    }

    @Test
    void decideReturnsActionInRange() {
        HierarchicalBrain brain = new HierarchicalBrain(rng);

        for (long sensors = 0; sensors < 1000; sensors++) {
            int action = brain.decide(sensors);
            assertThat(action)
                    .as("sensors=%d", sensors)
                    .isBetween(0, 31);
        }
    }

    @Test
    void decideIsDeterministic() {
        HierarchicalBrain brain = new HierarchicalBrain(new Random(123));

        long testSensor = 0xABCDE;

        int action1 = brain.decide(testSensor);
        int action2 = brain.decide(testSensor);
        int action3 = brain.decide(testSensor);

        assertThat(action1).isEqualTo(action2);
        assertThat(action1).isEqualTo(action3);
    }

    @Test
    void sameSeedProducesSameBrain() {
        HierarchicalBrain brain1 = new HierarchicalBrain(new Random(1));
        HierarchicalBrain brain2 = new HierarchicalBrain(new Random(1));

        for (long sensors = 0; sensors < 500; sensors++) {
            assertThat(brain2.decide(sensors))
                    .as("sensors=%d", sensors)
                    .isEqualTo(brain1.decide(sensors));
        }
    }

    @Test
    void randomBrainsHaveLowCollisionRate() {
        // Different seeds should produce different outputs most of the time
        HierarchicalBrain brain1 = new HierarchicalBrain(new Random(1));
        HierarchicalBrain brain2 = new HierarchicalBrain(new Random(2));

        int different = 0;
        int total = 500;

        for (long sensors = 0; sensors < total; sensors++) {
            if (brain1.decide(sensors) != brain2.decide(sensors)) {
                different++;
            }
        }

        // At least 70% of outputs should differ between different random brains
        double diffRate = (double) different / total;
        assertThat(diffRate).as("Different outputs ratio should be high").isGreaterThan(0.7);
    }

    @Test
    void customLayerBrain() {
        NeuronLayer sensors = new NeuronLayer(4, 3, rng);
        NeuronLayer features = new NeuronLayer(3, 3, rng);
        NeuronLayer actions = new NeuronLayer(2, 3, rng);

        HierarchicalBrain brain = new HierarchicalBrain(sensors, features, actions);

        assertThat(brain.sensorLayer().outputWidth()).isEqualTo(4);
        assertThat(brain.featureLayer().outputWidth()).isEqualTo(3);
        assertThat(brain.actionLayer().outputWidth()).isEqualTo(2);

        // Action should be in range [0, 3] for 2-bit output
        int action = brain.decide(0xAAAAA);
        assertThat(action).isBetween(0, 3);
    }

    @Test
    void decideWithAllZeroSensors() {
        HierarchicalBrain brain = new HierarchicalBrain(new Random(777));

        for (int trial = 0; trial < 10; trial++) {
            int action = brain.decide(0L);
            assertThat(action).isBetween(0, 31);

            // Deterministic
            int action2 = brain.decide(0L);
            assertThat(action2).isEqualTo(action);
        }
    }

    @Test
    void decideWithAllOneSensors() {
        HierarchicalBrain brain = new HierarchicalBrain(new Random(888));

        // Set all 20 sensor bits
        long allOnes = (1L << 20) - 1;

        for (int trial = 0; trial < 10; trial++) {
            int action = brain.decide(allOnes);
            assertThat(action).isBetween(0, 31);

            int action2 = brain.decide(allOnes);
            assertThat(action2).isEqualTo(action);
        }
    }

    @Test
    void roundtripViaAvroBytes() {
        HierarchicalBrain original = new HierarchicalBrain(new Random(42));

        byte[] bytes = original.toAvroBytes();
        assertThat(bytes).isNotEmpty();

        HierarchicalBrain restored = HierarchicalBrain.fromAvroBytes(bytes);

        // Verify same dimensions
        assertThat(restored.sensorLayer().outputWidth())
                .isEqualTo(original.sensorLayer().outputWidth());
        assertThat(restored.featureLayer().outputWidth())
                .isEqualTo(original.featureLayer().outputWidth());
        assertThat(restored.actionLayer().outputWidth())
                .isEqualTo(original.actionLayer().outputWidth());

        // Verify identical behavior
        for (long sensors = 0; sensors < 500; sensors++) {
            assertThat(restored.decide(sensors))
                    .as("sensors=%d", sensors)
                    .isEqualTo(original.decide(sensors));
        }
    }

    @Test
    void roundtripWithCustomLayers() {
        NeuronLayer sensors = new NeuronLayer(3, 4, new Random(10));
        NeuronLayer features = new NeuronLayer(2, 4, new Random(20));
        NeuronLayer actions = new NeuronLayer(2, 4, new Random(30));

        HierarchicalBrain original = new HierarchicalBrain(sensors, features, actions);

        byte[] bytes = original.toAvroBytes();
        HierarchicalBrain restored = HierarchicalBrain.fromAvroBytes(bytes);

        for (long s = 0; s < 200; s++) {
            assertThat(restored.decide(s))
                    .as("sensors=%d", s)
                    .isEqualTo(original.decide(s));
        }
    }

    @Test
    void toStringIsDescriptive() {
        HierarchicalBrain brain = new HierarchicalBrain(rng);
        String str = brain.toString();

        assertThat(str).contains("HierarchicalBrain")
                .contains("sensor")
                .contains("feature")
                .contains("action");
    }

    @Test
    void layerAccessorsReturnSameObjects() {
        HierarchicalBrain brain = new HierarchicalBrain(rng);

        NeuronLayer s1 = brain.sensorLayer();
        NeuronLayer s2 = brain.sensorLayer();

        assertThat(s1).isSameAs(s2);
    }
}
