package io.matrix.autonomy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The bounded in-memory proposal store.
 *
 * <p>The open queue has a fixed capacity. When it is full the store refuses the
 * proposal — it does not block, it does not grow, and it does not drop the
 * refusal silently (the consumer counts it).
 *
 * <p>The terminal history is bounded by its own capacity for the same reason: a
 * queue that grows without backpressure is a memory leak with a cognitive
 * vocabulary. The oldest terminal proposal is discarded when the history is
 * full; the open queue is never over its capacity.
 */
public final class BoundedImpulseProposalStore implements ImpulseProposalStore {

    private final int capacity;
    private final int historyCapacity;
    private final Map<String, ImpulseProposal> open = new LinkedHashMap<>();
    private final Map<String, ImpulseProposal> terminal = new LinkedHashMap<>();

    /**
     * @param capacity the maximum number of simultaneously open proposals; must
     *                 be positive
     */
    public BoundedImpulseProposalStore(int capacity) {
        this(capacity, capacity);
    }

    /**
     * @param capacity        the maximum number of simultaneously open proposals
     * @param historyCapacity the maximum number of terminal proposals retained
     */
    public BoundedImpulseProposalStore(int capacity, int historyCapacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (historyCapacity < 0) {
            throw new IllegalArgumentException("historyCapacity must be ≥ 0");
        }
        this.capacity = capacity;
        this.historyCapacity = historyCapacity;
    }

    @Override
    public boolean record(ImpulseProposal proposal) {
        if (proposal == null) {
            throw new IllegalArgumentException("proposal must not be null");
        }
        if (open.size() >= capacity) {
            return false;
        }
        open.put(proposal.id(), proposal);
        return true;
    }

    @Override
    public Optional<ImpulseProposal> byId(String id) {
        ImpulseProposal live = open.get(id);
        return live != null ? Optional.of(live) : Optional.ofNullable(terminal.get(id));
    }

    @Override
    public boolean hasOpen(String dedupKey) {
        for (ImpulseProposal proposal : open.values()) {
            if (proposal.dedupKey().equals(dedupKey)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<ImpulseProposal> open() {
        return List.copyOf(open.values());
    }

    @Override
    public List<ImpulseProposal> all() {
        List<ImpulseProposal> all = new ArrayList<>(open.values());
        all.addAll(terminal.values());
        return List.copyOf(all);
    }

    @Override
    public Optional<ImpulseProposal> close(String id, ImpulseProposal.ProposalState state) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        if (state == ImpulseProposal.ProposalState.PROPOSED) {
            throw new IllegalArgumentException("PROPOSED is not a terminal state");
        }
        ImpulseProposal live = open.remove(id);
        if (live == null) {
            return Optional.empty();
        }
        ImpulseProposal closed = new ImpulseProposal(live.id(), live.impulse(), live.kind(),
                live.cycle(), live.source(), live.envelopeSpent(), live.rowsAllocated(),
                live.shadowPrice(), live.expiresAtNanos(), state);
        terminal.put(id, closed);
        while (terminal.size() > historyCapacity) {
            String oldest = terminal.keySet().iterator().next();
            terminal.remove(oldest);
        }
        return Optional.of(closed);
    }

    @Override
    public int openCount() {
        return open.size();
    }

    @Override
    public int capacity() {
        return capacity;
    }
}
