package io.matrix.signals;

/**
 * Federated encoder (SPEC-004 / DESIGN-16 §3, W-F): the perception
 * pipeline's contract for converting a {@link SensorPacket} into the
 * downstream {@link SensorPacket.Encoded} form, possibly via multiple
 * collaborators (one per modality) without leaking the original payload
 * beyond the encoder boundary.
 *
 * <p>A federated encoder dispatches to a {@link SignalModule} per
 * modality; the encoder is stateless and the modules are versioned, so
 * the encoded vector is fully determined by the packet and the module
 * versions in the registry.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link DefaultFederatedEncoder} — the canonical implementation
 *       that consults a {@link SignalModuleRegistry}.</li>
 * </ul>
 */
public interface FederatedEncoder {

    /**
     * Encode a packet using the registered module for its modality.
     *
     * @param packet the sensor observation
     * @return the encoded packet (signal vector + same metadata)
     * @throws IllegalArgumentException if no module is registered for the
     *         packet's modality, or the registered module's version does
     *         not satisfy the encoder's compatibility window.
     */
    SensorPacket.Encoded encode(SensorPacket packet);

    /**
     * Canonical implementation: looks up the {@link SignalModule} for the
     * packet's modality in a {@link SignalModuleRegistry} and forwards to
     * {@link SensorPacket#encodeWith(SignalModule)}.
     */
    final class DefaultFederatedEncoder implements FederatedEncoder {
        private final SignalModuleRegistry registry;

        public DefaultFederatedEncoder(SignalModuleRegistry registry) {
            this.registry = java.util.Objects.requireNonNull(registry, "registry");
        }

        @Override
        public SensorPacket.Encoded encode(SensorPacket packet) {
            SignalModule module = registry.get(packet.modality())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "no module registered for modality: " + packet.modality()));
            return packet.encodeWith(module);
        }
    }
}