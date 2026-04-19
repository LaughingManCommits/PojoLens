package laughing.man.commits.tree;

import laughing.man.commits.internal.FluentEngine;
import laughing.man.commits.PojoLensSql;
import laughing.man.commits.PojoLensTree;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Sort;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PojoLensTreeTest {

    @Test
    public void subtreeOfShouldMatchBuilderAndReturnBfsDescendants() {
        List<OrgNode> rows = sampleOrg();

        List<OrgNode> oneLiner = PojoLensTree.subtreeOf(rows, node -> node.id, node -> node.managerId, 1);
        List<OrgNode> builder = PojoLensTree
                .fromFlat(rows, node -> node.id, node -> node.managerId)
                .subtree(1)
                .toList();

        assertEquals(names(builder), names(oneLiner));
        assertEquals(List.of("CEO", "CTO", "CFO", "Platform", "Quality", "Accounting"), names(builder));
    }

    @Test
    public void forestShouldPreserveRootOrderAndSiblingOrder() {
        List<OrgNode> forest = PojoLensTree
                .fromFlat(sampleOrg(), node -> node.id, node -> node.managerId)
                .toList();

        assertEquals(
                List.of("CEO", "Orphan", "Standalone", "CTO", "CFO", "Platform", "Quality", "Accounting"),
                names(forest)
        );
    }

    @Test
    public void maxDepthShouldStopAtRequestedDepth() {
        List<OrgNode> rows = PojoLensTree
                .fromFlat(sampleOrg(), node -> node.id, node -> node.managerId)
                .subtree(1)
                .maxDepth(1)
                .toList();

        assertEquals(List.of("CEO", "CTO", "CFO"), names(rows));
    }

    @Test
    public void pruneShouldKeepPrunedNodeAndExcludeItsDescendants() {
        List<OrgNode> rows = PojoLensTree
                .fromFlat(sampleOrg(), node -> node.id, node -> node.managerId)
                .subtree(1)
                .prune(node -> node.id != 2)
                .toList();

        assertEquals(List.of("CEO", "CTO", "CFO", "Accounting"), names(rows));
    }

    @Test
    public void leavesOnlyShouldReturnOnlyIndexedLeaves() {
        List<OrgNode> rows = PojoLensTree
                .fromFlat(sampleOrg(), node -> node.id, node -> node.managerId)
                .subtree(1)
                .leavesOnly()
                .toList();

        assertEquals(List.of("Platform", "Quality", "Accounting"), names(rows));
    }

    @Test
    public void entriesShouldExposeDepthAndParentWithoutMutatingRows() {
        List<TreeEntry<OrgNode>> entries = PojoLensTree
                .fromFlat(sampleOrg(), node -> node.id, node -> node.managerId)
                .subtree(1)
                .toEntries();

        assertEquals(List.of("CEO", "CTO", "CFO", "Platform", "Quality", "Accounting"),
                names(entries.stream().map(TreeEntry::node).toList()));
        assertEquals(0, entries.get(0).depth());
        assertNull(entries.get(0).parent());
        assertEquals(1, entries.get(1).depth());
        assertSame(entries.get(0).node(), entries.get(1).parent());
        assertEquals(2, entries.get(3).depth());
        assertSame(entries.get(1).node(), entries.get(3).parent());

        List<OrgNode> nodes = PojoLensTree
                .fromFlat(sampleOrg(), node -> node.id, node -> node.managerId)
                .subtree(1)
                .toList();
        assertEquals(names(nodes), names(entries.stream().map(TreeEntry::node).toList()));
    }

    @Test
    public void invalidIdsAndCyclesShouldFailFast() {
        assertThrows(IllegalArgumentException.class, () -> PojoLensTree
                .fromFlat(Arrays.asList(
                        new OrgNode(1, null, "A", "Engineering", 100),
                        new OrgNode(1, null, "B", "Engineering", 90)
                ), node -> node.id, node -> node.managerId)
                .toList());

        assertThrows(IllegalArgumentException.class, () -> PojoLensTree
                .fromFlat(List.of(new OrgNode(null, null, "No ID", "Engineering", 100)),
                        node -> node.id, node -> node.managerId)
                .toList());

        IllegalArgumentException cycle = assertThrows(IllegalArgumentException.class, () -> PojoLensTree
                .fromFlat(Arrays.asList(
                        new OrgNode(1, 3, "A", "Engineering", 100),
                        new OrgNode(2, 1, "B", "Engineering", 90),
                        new OrgNode(3, 2, "C", "Engineering", 80)
                ), node -> node.id, node -> node.managerId)
                .toList());
        assertTrue(cycle.getMessage().contains("Cycle detected"));
    }

    @Test
    public void nullAndMissingInputShouldHaveDocumentedBehavior() {
        assertEquals(List.of(), PojoLensTree
                .<OrgNode, Integer>fromFlat(null, node -> node.id, node -> node.managerId)
                .toList());

        assertThrows(NullPointerException.class, () -> PojoLensTree
                .fromFlat(sampleOrg(), null, node -> node.managerId));
        assertThrows(NullPointerException.class, () -> PojoLensTree
                .fromFlat(sampleOrg(), node -> node.id, null));
        assertThrows(NullPointerException.class, () -> PojoLensTree
                .fromFlat(sampleOrg(), node -> node.id, node -> node.managerId)
                .subtree(null));
        assertThrows(IllegalArgumentException.class, () -> PojoLensTree
                .fromFlat(sampleOrg(), node -> node.id, node -> node.managerId)
                .maxDepth(-1));

        assertEquals(List.of(), PojoLensTree
                .fromFlat(sampleOrg(), node -> node.id, node -> node.managerId)
                .subtree(999)
                .toList());

        OrgNode single = new OrgNode(10, null, "Solo", "Engineering", 100);
        List<TreeEntry<OrgNode>> entries = PojoLensTree
                .fromFlat(List.of(single), node -> node.id, node -> node.managerId)
                .toEntries();
        assertEquals(1, entries.size());
        assertSame(single, entries.get(0).node());
        assertEquals(0, entries.get(0).depth());
        assertNull(entries.get(0).parent());
    }

    @Test
    public void subtreeRowsShouldFeedExistingFluentAndSqlLikeEngines() {
        List<OrgNode> subtree = PojoLensTree.subtreeOf(sampleOrg(), node -> node.id, node -> node.managerId, 1);

        List<OrgNode> fluent = FluentEngine.newQueryBuilder(subtree)
                .addRule("department", "Engineering", Clauses.EQUAL)
                .addOrder("salary", 1)
                .limit(3)
                .initFilter()
                .filter(Sort.DESC, OrgNode.class);

        assertEquals(List.of("CEO", "CTO", "Platform"), names(fluent));

        List<OrgNode> sql = PojoLensSql
                .parse("where department = 'Engineering' order by salary desc limit 3")
                .filter(subtree, OrgNode.class);

        assertEquals(List.of("CEO", "CTO", "Platform"), names(sql));
    }

    private static List<OrgNode> sampleOrg() {
        return Arrays.asList(
                new OrgNode(1, null, "CEO", "Engineering", 200),
                new OrgNode(2, 1, "CTO", "Engineering", 180),
                new OrgNode(3, 1, "CFO", "Finance", 175),
                new OrgNode(4, 2, "Platform", "Engineering", 150),
                new OrgNode(5, 2, "Quality", "Engineering", 130),
                new OrgNode(6, 3, "Accounting", "Finance", 125),
                new OrgNode(7, 99, "Orphan", "Support", 95),
                new OrgNode(8, null, "Standalone", "Engineering", 90)
        );
    }

    private static List<String> names(List<OrgNode> rows) {
        return rows.stream().map(row -> row.name).toList();
    }

    public static class OrgNode {
        public Integer id;
        public Integer managerId;
        public String name;
        public String department;
        public int salary;

        public OrgNode() {
        }

        public OrgNode(Integer id, Integer managerId, String name, String department, int salary) {
            this.id = id;
            this.managerId = managerId;
            this.name = name;
            this.department = department;
            this.salary = salary;
        }
    }
}
