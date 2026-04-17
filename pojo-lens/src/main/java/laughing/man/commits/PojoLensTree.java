package laughing.man.commits;

import laughing.man.commits.tree.TreeTraversalBuilder;

import java.util.List;
import java.util.function.Function;

/**
 * Flat parent-ID tree helpers that return rows for the existing query engine.
 */
public final class PojoLensTree {

    private PojoLensTree() {
    }

    /**
     * Starts a tree traversal builder over a flat list with parent IDs.
     *
     * @param items source rows
     * @param idFn extracts each node ID
     * @param parentIdFn extracts each parent ID
     * @param <T> row type
     * @param <K> ID type
     * @return traversal builder
     */
    public static <T, K> TreeTraversalBuilder<T, K> fromFlat(
            final List<T> items,
            final Function<? super T, ? extends K> idFn,
            final Function<? super T, ? extends K> parentIdFn
    ) {
        return new TreeTraversalBuilder<>(items, idFn, parentIdFn);
    }

    /**
     * Returns a subtree rooted at the supplied ID.
     *
     * @param items source rows
     * @param idFn extracts each node ID
     * @param parentIdFn extracts each parent ID
     * @param rootId root ID
     * @param <T> row type
     * @param <K> ID type
     * @return subtree rows in breadth-first order
     */
    public static <T, K> List<T> subtreeOf(
            final List<T> items,
            final Function<? super T, ? extends K> idFn,
            final Function<? super T, ? extends K> parentIdFn,
            final K rootId
    ) {
        return fromFlat(items, idFn, parentIdFn).subtree(rootId).toList();
    }
}
