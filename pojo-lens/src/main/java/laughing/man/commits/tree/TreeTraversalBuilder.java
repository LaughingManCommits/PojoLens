package laughing.man.commits.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Builder for deterministic traversal over a flat parent-ID list.
 *
 * @param <T> row type
 * @param <K> ID type
 */
public final class TreeTraversalBuilder<T, K> {

    /** Source rows in caller-provided order. */
    private final List<T> items;

    /** Extracts each node ID. */
    private final Function<? super T, ? extends K> idFn;

    /** Extracts each parent ID. */
    private final Function<? super T, ? extends K> parentIdFn;

    /** Whether traversal starts at one explicit root ID. */
    private boolean scopedToSubtree;

    /** Explicit root ID when subtree scope is enabled. */
    private K rootId;

    /** Optional maximum traversal depth. */
    private Integer maxDepth;

    /** Optional descendant-pruning predicate. */
    private Predicate<? super T> prunePredicate;

    /** Whether output should include only indexed leaves. */
    private boolean leavesOnly;

    /**
     * Creates a traversal builder over flat parent-ID rows.
     *
     * @param sourceItems source rows
     * @param nodeIdFn extracts each node ID
     * @param nodeParentIdFn extracts each parent ID
     */
    public TreeTraversalBuilder(
            final List<T> sourceItems,
            final Function<? super T, ? extends K> nodeIdFn,
            final Function<? super T, ? extends K> nodeParentIdFn
    ) {
        this.items = sourceItems == null
                ? Collections.emptyList()
                : new ArrayList<>(sourceItems);
        this.idFn = Objects.requireNonNull(nodeIdFn, "idFn");
        this.parentIdFn = Objects.requireNonNull(nodeParentIdFn, "parentIdFn");
    }

    /**
     * Scopes traversal to one root ID.
     *
     * @param requestedRootId root ID
     * @return this builder
     */
    public TreeTraversalBuilder<T, K> subtree(final K requestedRootId) {
        this.rootId = Objects.requireNonNull(requestedRootId, "rootId");
        this.scopedToSubtree = true;
        return this;
    }

    /**
     * Stops traversal after the requested depth.
     *
     * @param requestedMaxDepth maximum depth; roots are depth {@code 0}
     * @return this builder
     */
    public TreeTraversalBuilder<T, K> maxDepth(final int requestedMaxDepth) {
        if (requestedMaxDepth < 0) {
            throw new IllegalArgumentException(
                    "maxDepth must be greater than or equal to 0");
        }
        this.maxDepth = requestedMaxDepth;
        return this;
    }

    /**
     * Includes a node but skips descendants when the predicate returns false.
     *
     * @param predicate pruning predicate
     * @return this builder
     */
    public TreeTraversalBuilder<T, K> prune(
            final Predicate<? super T> predicate) {
        this.prunePredicate = Objects.requireNonNull(predicate, "predicate");
        return this;
    }

    /**
     * Includes only indexed leaves in output.
     *
     * @return this builder
     */
    public TreeTraversalBuilder<T, K> leavesOnly() {
        this.leavesOnly = true;
        return this;
    }

    /**
     * Returns traversed rows.
     *
     * @return rows in deterministic breadth-first order
     */
    public List<T> toList() {
        List<TreeEntry<T>> entries = toEntries();
        List<T> rows = new ArrayList<>(entries.size());
        for (TreeEntry<T> entry : entries) {
            rows.add(entry.node());
        }
        return rows;
    }

    /**
     * Returns traversed rows with depth and parent metadata.
     *
     * @return entries in deterministic breadth-first order
     */
    public List<TreeEntry<T>> toEntries() {
        if (items.isEmpty()) {
            return Collections.emptyList();
        }

        Indexes<T, K> indexes = buildIndexes();
        detectCycles(indexes);

        List<T> startNodes = startNodes(indexes);
        if (startNodes.isEmpty()) {
            return Collections.emptyList();
        }

        List<TreeEntry<T>> result = new ArrayList<>();
        Queue<TreeEntry<T>> queue = new ArrayDeque<>();
        for (T root : startNodes) {
            queue.add(new TreeEntry<>(root, 0, null));
        }

        while (!queue.isEmpty()) {
            TreeEntry<T> entry = queue.remove();
            T node = entry.node();
            K id = indexes.idByNode.get(node);
            List<T> children = indexes.childrenByParentId.getOrDefault(
                    id,
                    Collections.emptyList()
            );
            if (!leavesOnly || children.isEmpty()) {
                result.add(entry);
            }

            if (shouldDescend(node, entry.depth())) {
                for (T child : children) {
                    queue.add(new TreeEntry<>(child, entry.depth() + 1, node));
                }
            }
        }

        return result;
    }

    private boolean shouldDescend(final T node, final int depth) {
        if (maxDepth != null && depth >= maxDepth) {
            return false;
        }
        return prunePredicate == null || prunePredicate.test(node);
    }

    private List<T> startNodes(final Indexes<T, K> indexes) {
        if (scopedToSubtree) {
            T root = indexes.nodeById.get(rootId);
            if (root == null) {
                return Collections.emptyList();
            }
            return List.of(root);
        }

        List<T> roots = new ArrayList<>();
        for (T item : items) {
            K id = indexes.idByNode.get(item);
            K parentId = indexes.parentIdById.get(id);
            if (parentId == null || !indexes.nodeById.containsKey(parentId)) {
                roots.add(item);
            }
        }
        return roots;
    }

    private Indexes<T, K> buildIndexes() {
        LinkedHashMap<K, T> nodeById = new LinkedHashMap<>();
        Map<T, K> idByNode = new IdentityHashMap<>();
        LinkedHashMap<K, K> parentIdById = new LinkedHashMap<>();

        for (T item : items) {
            K id = idFn.apply(item);
            if (id == null) {
                throw new IllegalArgumentException(
                        "Tree node ID must not be null");
            }
            if (nodeById.containsKey(id)) {
                throw new IllegalArgumentException(
                        "Duplicate tree node ID: " + id);
            }
            nodeById.put(id, item);
            idByNode.put(item, id);
            parentIdById.put(id, parentIdFn.apply(item));
        }

        LinkedHashMap<K, List<T>> childrenByParentId = new LinkedHashMap<>();
        for (T item : items) {
            K id = idByNode.get(item);
            K parentId = parentIdById.get(id);
            if (parentId != null && nodeById.containsKey(parentId)) {
                childrenByParentId
                        .computeIfAbsent(parentId, ignored -> new ArrayList<>())
                        .add(item);
            }
        }

        return new Indexes<>(
                nodeById,
                idByNode,
                parentIdById,
                childrenByParentId
        );
    }

    private void detectCycles(final Indexes<T, K> indexes) {
        Map<K, VisitState> states = new HashMap<>();
        for (K id : indexes.nodeById.keySet()) {
            detectCycleFrom(id, indexes, states);
        }
    }

    private void detectCycleFrom(
            final K id,
            final Indexes<T, K> indexes,
            final Map<K, VisitState> states
    ) {
        VisitState state = states.get(id);
        if (state == VisitState.VISITING) {
            throw new IllegalArgumentException(
                    "Cycle detected in parent-ID tree at ID: " + id);
        }
        if (state == VisitState.VISITED) {
            return;
        }

        states.put(id, VisitState.VISITING);
        K parentId = indexes.parentIdById.get(id);
        if (parentId != null && indexes.nodeById.containsKey(parentId)) {
            detectCycleFrom(parentId, indexes, states);
        }
        states.put(id, VisitState.VISITED);
    }

    private enum VisitState {
        /** Node is on the active parent walk. */
        VISITING,

        /** Node and its parent chain are cycle-free. */
        VISITED
    }

    private record Indexes<T, K>(
            LinkedHashMap<K, T> nodeById,
            Map<T, K> idByNode,
            LinkedHashMap<K, K> parentIdById,
            LinkedHashMap<K, List<T>> childrenByParentId
    ) {
    }
}
