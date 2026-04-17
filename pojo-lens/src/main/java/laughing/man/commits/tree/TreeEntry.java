package laughing.man.commits.tree;

/**
 * Node plus traversal metadata produced by tree traversal.
 *
 * @param node traversed node
 * @param depth depth from traversal root
 * @param parent parent node, or {@code null} for roots
 * @param <T> node type
 */
public record TreeEntry<T>(T node, int depth, T parent) {
}
