package io.matrix.reasoning;

import java.util.BitSet;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Feedback perception (Wave C): a {@link Supplier} that returns the
 * last tick's action output as the next perception input, closing
 * the brain's feedback loop.
 *
 * <p>Pattern: pass an instance to {@link ConsciousnessLoop}'s
 * constructor; after each tick the loop calls
 * {@link #lastAction(BitSet)} with the chain output; the next
 * tick's perception is that output (zero-padded to the input width).
 *
 * <p>If no action has been recorded yet, falls back to a static
 * perception seed so the loop has something to chew on at startup.
 */
public final class FeedbackPerception implements Supplier<BitSet> {

    private final AtomicReference<BitSet> lastAction = new AtomicReference<>();
    private final Supplier<BitSet> seed;
    private final int inputWidth;

    public FeedbackPerception(int inputWidth, Supplier<BitSet> seed) {
        if (inputWidth < 1) throw new IllegalArgumentException("inputWidth >= 1");
        this.inputWidth = inputWidth;
        this.seed = Objects.requireNonNull(seed, "seed");
    }

    @Override
    public BitSet get() {
        BitSet prev = lastAction.get();
        if (prev == null) {
            return seed.get();
        }
        // zero-pad / truncate to input width
        BitSet out = new BitSet(inputWidth);
        for (int i = 0; i < Math.min(inputWidth, prev.length()); i++) {
            if (prev.get(i)) out.set(i);
        }
        return out;
    }

    /** Record the most recent action output; called by ConsciousnessLoop. */
    public void lastAction(BitSet actionOutput) {
        lastAction.set(actionOutput);
    }

    public int inputWidth() { return inputWidth; }
}