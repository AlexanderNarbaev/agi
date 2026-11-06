package io.matrix.cognitive.neuromodulation;

import io.matrix.cognitive.DriveSignal;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * W44.3 — the complete internal regulatory state at one moment.
 *
 * <p>One immutable snapshot of every drive the system currently holds. Immutable because a
 * regulatory state that can be mutated while a decision is being made cannot be audited: the
 * trace has to record the state as it was when the decision was taken.</p>
 *
 * <p>A dimension that is absent from the map is at its neutral value. Absence is therefore
 * meaningful and cheap, and it is why the neutral state can be built without naming eight
 * zeros.</p>
 */
public record RegulatoryState(Map<DriveSignal.Dimension, DriveSignal> drives) {

    public RegulatoryState {
        Map<DriveSignal.Dimension, DriveSignal> copy = new EnumMap<>(DriveSignal.Dimension.class);
        if (drives != null) {
            copy.putAll(drives);
        }
        drives = Map.copyOf(copy);
    }

    /** The state where nothing is driving anything. */
    public static RegulatoryState neutral() {
        return new RegulatoryState(Map.of());
    }

    /** The level of one dimension, or zero when it is not present. */
    public double level(DriveSignal.Dimension dimension) {
        DriveSignal drive = drives.get(dimension);
        return drive == null ? 0.0 : drive.level();
    }

    /** The drive for one dimension, if the system holds it. */
    public Optional<DriveSignal> drive(DriveSignal.Dimension dimension) {
        return Optional.ofNullable(drives.get(dimension));
    }

    /** The same state with one dimension replaced. */
    public RegulatoryState with(DriveSignal drive) {
        Map<DriveSignal.Dimension, DriveSignal> copy = new EnumMap<>(DriveSignal.Dimension.class);
        copy.putAll(drives);
        copy.put(drive.dimension(), drive);
        return new RegulatoryState(copy);
    }

    /** The dimensions the system currently holds, in enum order. */
    public java.util.Set<DriveSignal.Dimension> held() {
        return drives.keySet();
    }
}
