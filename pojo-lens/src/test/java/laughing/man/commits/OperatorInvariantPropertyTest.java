package laughing.man.commits;

import laughing.man.commits.domain.Foo;
import laughing.man.commits.enums.Clauses;
import laughing.man.commits.enums.Separator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OperatorInvariantPropertyTest {

    @Test
    public void numericOperatorsShouldRespectComplementInvariants() throws Exception {
        Random random = new Random(20260303L);
        for (int iteration = 0; iteration < 50; iteration++) {
            List<Foo> source = randomFoos(random, 40);
            int pivot = random.nextInt(101);

            Set<String> eq = rowKeys(filter(source, Clauses.EQUAL, pivot));
            Set<String> ne = rowKeys(filter(source, Clauses.NOT_EQUAL, pivot));
            Set<String> gt = rowKeys(filter(source, Clauses.BIGGER, pivot));
            Set<String> notGt = rowKeys(filter(source, Clauses.NOT_BIGGER, pivot));
            Set<String> lt = rowKeys(filter(source, Clauses.SMALLER, pivot));
            Set<String> notLt = rowKeys(filter(source, Clauses.NOT_SMALLER, pivot));

            assertPartition(source.size(), eq, ne);
            assertPartition(source.size(), gt, notGt);
            assertPartition(source.size(), lt, notLt);
        }
    }

    private static List<Foo> randomFoos(Random random, int count) {
        List<Foo> rows = new ArrayList<>();
        Date now = new Date();
        for (int i = 0; i < count; i++) {
            rows.add(new Foo("row-" + i, now, random.nextInt(101)));
        }
        return rows;
    }

    private static List<Foo> filter(List<Foo> source, Clauses clause, int compareValue) throws Exception {
        return PojoLens.newQueryBuilder(source)
                .addRule("integerField", compareValue, clause, Separator.OR)
                .initFilter()
                .filter(Foo.class);
    }

    private static Set<String> rowKeys(List<Foo> rows) {
        return rows.stream()
                .map(Foo::getStringField)
                .collect(Collectors.toSet());
    }

    private static void assertPartition(int totalRows, Set<String> first, Set<String> second) {
        Set<String> union = first.stream().collect(Collectors.toSet());
        union.addAll(second);
        Set<String> intersection = first.stream()
                .filter(second::contains)
                .collect(Collectors.toSet());

        assertEquals(0, intersection.size());
        assertEquals(totalRows, union.size());
    }
}

