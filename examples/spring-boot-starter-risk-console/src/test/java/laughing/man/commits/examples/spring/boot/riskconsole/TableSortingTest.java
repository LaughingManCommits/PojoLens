package laughing.man.commits.examples.spring.boot.riskconsole;

import org.junit.jupiter.api.Test;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableSortingTest extends PlaywrightE2EBase {

    @Test
    void transactionSortingWorksForAmountAndCreatedAt() {
        page.navigate(baseUrl());
        activateTab("operationsTabButton");

        String firstDefaultRow = page.locator("#transactionsBody tr").first().textContent();
        jsClick("#nextPageButton");
        page.waitForTimeout(700);
        String firstDefaultRowPageTwo = page.locator("#transactionsBody tr").first().textContent();
        assertTrue(!firstDefaultRowPageTwo.equals(firstDefaultRow), "Default id sort next page was empty or unchanged");
        jsClick("#previousPageButton");
        page.waitForTimeout(700);
        assertTrue(page.locator("#transactionsBody tr").first().textContent().equals(firstDefaultRow),
                "Previous page did not restore default first page");

        page.selectOption("#transactionSortField", "amount");
        page.selectOption("#transactionSortDirection", "desc");
        page.waitForTimeout(700);
        double firstAmountDesc = moneyCellValue(0);
        double secondAmountDesc = moneyCellValue(1);
        assertTrue(firstAmountDesc >= secondAmountDesc, "Amount desc sort failed");

        page.selectOption("#transactionSortDirection", "asc");
        page.waitForTimeout(700);
        double firstAmountAsc = moneyCellValue(0);
        double secondAmountAsc = moneyCellValue(1);
        assertTrue(firstAmountAsc <= secondAmountAsc, "Amount asc sort failed");

        page.selectOption("#transactionSortField", "createdAt");
        page.selectOption("#transactionSortDirection", "desc");
        page.waitForTimeout(700);
        var firstDateDesc = createdAtValue(0);
        var secondDateDesc = createdAtValue(1);
        assertTrue(!firstDateDesc.isBefore(secondDateDesc), "CreatedAt desc sort failed");

        String firstRowBeforeNext = page.locator("#transactionsBody tr").first().textContent();
        assertThat(page.locator("#transactionsPagingMeta")).containsText("Page 1");
        jsClick("#nextPageButton");
        page.waitForTimeout(700);
        String firstRowAfterNext = page.locator("#transactionsBody tr").first().textContent();
        assertTrue(!firstRowAfterNext.equals(firstRowBeforeNext), "Next page did not change visible rows");
        assertThat(page.locator("#transactionsPagingMeta")).containsText("Page 2");

        jsClick("#previousPageButton");
        page.waitForTimeout(700);
        String firstRowAfterPrevious = page.locator("#transactionsBody tr").first().textContent();
        assertTrue(firstRowAfterPrevious.equals(firstRowBeforeNext), "Previous page did not restore first page rows");
        assertThat(page.locator("#transactionsPagingMeta")).containsText("Page 1");
        capture("transaction-sorting.png");
        assertNoConsoleErrors();
    }
}
