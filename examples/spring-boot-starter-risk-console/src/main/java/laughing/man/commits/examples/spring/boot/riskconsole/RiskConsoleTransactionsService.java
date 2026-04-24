package laughing.man.commits.examples.spring.boot.riskconsole;

import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TransactionDetailHeader;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TransactionDetailPayload;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TransactionTableRow;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TransactionTimelineEvent;
import laughing.man.commits.examples.spring.boot.riskconsole.RiskConsoleTypes.TransactionsPayload;
import laughing.man.commits.sqllike.SqlLikeCursor;
import laughing.man.commits.sqllike.SqlLikeQuery;
import laughing.man.commits.table.TabularRows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
class RiskConsoleTransactionsService {

    private final RiskConsoleQuerySupport support;

    RiskConsoleTransactionsService(RiskConsoleQuerySupport support) {
        this.support = support;
    }

    TransactionsPayload transactions(String range,
                                     String region,
                                     String status,
                                     String riskBand,
                                     String segment,
                                     String search,
                                     Integer limit,
                                     String sortBy,
                                     String sortDirection,
                                     String cursor) {
        RiskConsoleFilterInput filters = support.filters(range, region, status, riskBand, segment, search);
        RiskConsoleTimeWindow window = support.timeWindow(filters.range());
        int pageSize = Math.max(10, Math.min(limit == null ? 25 : limit, 100));
        List<RiskConsoleTypes.TransactionRecord> rows = support.repository().loadTransactionRecords(window.start(), window.end());
        String normalizedSortBy = support.normalizeSortBy(sortBy);
        String normalizedSortDirection = support.normalizeSortDirection(sortDirection);

        Map<String, Object> params = new LinkedHashMap<>();
        List<String> clauses = new ArrayList<>();
        if (!filters.region().equals("ALL")) {
            clauses.add("merchantRegion = :region");
            params.put("region", filters.region());
        }
        if (!filters.status().equals("ALL")) {
            clauses.add("status = :status");
            params.put("status", filters.status());
        }
        if (!filters.riskBand().equals("ALL")) {
            clauses.add("riskBand = :riskBand");
            params.put("riskBand", filters.riskBand());
        }
        if (!filters.segment().equals("ALL")) {
            clauses.add("merchantSegment = :segment");
            params.put("segment", filters.segment());
        }
        if (!filters.search().isBlank()) {
            clauses.add("searchText contains :search");
            params.put("search", filters.search().toLowerCase(java.util.Locale.ROOT));
        }

        StringBuilder sql = new StringBuilder(
                "select id, createdAt, merchantName, merchantRegion, merchantSegment, status, riskBand, amount, "
                        + "currency, paymentMethod, riskScore, reviewStatus, failureCode");
        if (!clauses.isEmpty()) {
            sql.append(" where ").append(String.join(" and ", clauses));
        }
        sql.append(" order by ").append(support.orderByClause(normalizedSortBy, normalizedSortDirection));

        SqlLikeQuery query = support.runtime().parse(sql.toString());
        if (!params.isEmpty()) {
            query = query.params(params);
        }
        List<TransactionTableRow> sortedRows = query.filter(rows, TransactionTableRow.class);
        SortedTransactionPage page = pageSortedRows(sortedRows, pageSize, cursor, normalizedSortBy, normalizedSortDirection);
        return new TransactionsPayload(
                query.schema(TransactionTableRow.class).names(),
                TabularRows.toMaps(page.rows(), query.schema(TransactionTableRow.class)),
                page.loadedRows(),
                sortedRows.size(),
                pageSize,
                page.hasMore(),
                page.nextCursor()
        );
    }

    TransactionDetailPayload transactionDetail(String transactionId) {
        TransactionDetailHeader header = support.repository().loadTransactionDetail(transactionId);
        if (header == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found");
        }
        List<TransactionTimelineEvent> events = support.repository().loadTransactionTimeline(transactionId);
        return new TransactionDetailPayload(
                header,
                List.of("eventType", "createdAt", "actor", "detailText"),
                events.stream()
                        .map(event -> Map.<String, Object>of(
                                "eventType", event.eventType,
                                "createdAt", event.createdAt,
                                "actor", event.actor,
                                "detailText", event.detailText
                        ))
                        .toList()
        );
    }

    private SortedTransactionPage pageSortedRows(List<TransactionTableRow> rows,
                                                 int pageSize,
                                                 String cursor,
                                                 String sortBy,
                                                 String sortDirection) {
        int startIndex = 0;
        if (cursor != null && !cursor.isBlank()) {
            startIndex = findSortedStartIndex(rows, cursor, sortBy, sortDirection);
        }
        int endIndex = Math.min(startIndex + pageSize, rows.size());
        List<TransactionTableRow> pageRows = rows.subList(startIndex, endIndex);
        boolean hasMore = endIndex < rows.size();
        String nextCursor = hasMore && !pageRows.isEmpty()
                ? transactionCursor(pageRows.get(pageRows.size() - 1), sortBy, sortDirection)
                : null;
        return new SortedTransactionPage(pageRows, pageRows.size(), hasMore, nextCursor);
    }

    private int findSortedStartIndex(List<TransactionTableRow> rows,
                                     String cursor,
                                     String sortBy,
                                     String sortDirection) {
        SqlLikeCursor decoded;
        try {
            decoded = SqlLikeCursor.fromToken(cursor);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad cursor");
        }
        Object cursorSortBy = decoded.values().get("sortBy");
        Object cursorSortDirection = decoded.values().get("sortDirection");
        Object primaryValue = decoded.values().get("primary");
        Object idValue = decoded.values().get("id");
        if (!(cursorSortBy instanceof String cursorSortByText)
                || !(cursorSortDirection instanceof String cursorSortDirectionText)
                || !(idValue instanceof String idText)
                || !sortBy.equals(cursorSortByText)
                || !sortDirection.equals(cursorSortDirectionText)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad cursor");
        }
        for (int index = 0; index < rows.size(); index++) {
            TransactionTableRow row = rows.get(index);
            if (row.id.equals(idText) && sortValueMatches(row, sortBy, primaryValue)) {
                return index + 1;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad cursor");
    }

    private boolean sortValueMatches(TransactionTableRow row, String sortBy, Object primaryValue) {
        return switch (sortBy) {
            case "id" -> primaryValue instanceof String text && row.id.equals(text);
            case "createdAt" -> primaryValue instanceof String text && row.createdAt.equals(LocalDateTime.parse(text));
            case "amount" -> primaryValue instanceof Number number && Double.compare(row.amount, number.doubleValue()) == 0;
            case "riskScore" -> primaryValue instanceof Number number && row.riskScore == number.intValue();
            default -> false;
        };
    }

    private String transactionCursor(TransactionTableRow row, String sortBy, String sortDirection) {
        return SqlLikeCursor.of(Map.of(
                "sortBy", sortBy,
                "sortDirection", sortDirection,
                "primary", primaryCursorValue(row, sortBy),
                "id", row.id
        )).toToken();
    }

    private Object primaryCursorValue(TransactionTableRow row, String sortBy) {
        return switch (sortBy) {
            case "id" -> row.id;
            case "createdAt" -> row.createdAt.toString();
            case "amount" -> row.amount;
            case "riskScore" -> row.riskScore;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bad cursor");
        };
    }

    private record SortedTransactionPage(List<TransactionTableRow> rows,
                                         int loadedRows,
                                         boolean hasMore,
                                         String nextCursor) {
    }
}
