package laughing.man.commits.snapshot;

/**
 * Summary counts for snapshot comparison output.
 */
public final class SnapshotComparisonSummary {

    private final int currentCount;
    private final int previousCount;
    private final int addedCount;
    private final int removedCount;
    private final int changedCount;
    private final int unchangedCount;

    SnapshotComparisonSummary(int currentCount,
                              int previousCount,
                              int addedCount,
                              int removedCount,
                              int changedCount,
                              int unchangedCount) {
        this.currentCount = currentCount;
        this.previousCount = previousCount;
        this.addedCount = addedCount;
        this.removedCount = removedCount;
        this.changedCount = changedCount;
        this.unchangedCount = unchangedCount;
    }

    public int currentCount() {
        return currentCount;
    }

    public int previousCount() {
        return previousCount;
    }

    public int addedCount() {
        return addedCount;
    }

    public int removedCount() {
        return removedCount;
    }

    public int changedCount() {
        return changedCount;
    }

    public int unchangedCount() {
        return unchangedCount;
    }

    public int netRowDelta() {
        return addedCount - removedCount;
    }
}

