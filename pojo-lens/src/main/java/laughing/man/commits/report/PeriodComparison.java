package laughing.man.commits.report;

import java.util.Locale;

/**
 * Immutable value holding a current/previous metric pair with formatted delta helpers.
 *
 * <p>Obtain instances from {@link ReportComparisons}.
 */
public final class PeriodComparison {

    private final double currentValue;
    private final double previousValue;

    PeriodComparison(double currentValue, double previousValue) {
        this.currentValue = currentValue;
        this.previousValue = previousValue;
    }

    public double currentValue() {
        return currentValue;
    }

    public double previousValue() {
        return previousValue;
    }

    public double absoluteDelta() {
        return currentValue - previousValue;
    }

    /**
     * Relative change as a fraction (e.g. {@code 0.05} for +5 %).
     * Returns {@code Double.NaN} when {@code previousValue} is zero.
     */
    public double relativeDelta() {
        if (Math.abs(previousValue) < 1e-9) {
            return Double.NaN;
        }
        return (currentValue - previousValue) / Math.abs(previousValue);
    }

    /**
     * Percentage delta as a formatted string: {@code "+5%"}, {@code "-3%"},
     * {@code "flat"}, or {@code "new"} when the previous value was zero.
     */
    public String percentageDelta() {
        if (Math.abs(previousValue) < 1e-9) {
            return Math.abs(currentValue) < 1e-9 ? "flat" : "new";
        }
        long percent = Math.round(relativeDelta() * 100d);
        return percent > 0 ? "+" + percent + "%" : percent + "%";
    }

    /**
     * Basis-point delta as a formatted string: {@code "+0.5 pt"}, {@code "-1.2 pt"}.
     * Intended for rate/percentage fields (e.g. approval rates expressed as 0–1 fractions).
     */
    public String ratePointDelta() {
        double points = (currentValue - previousValue) * 100d;
        return String.format(Locale.ROOT, "%+.1f pt", points);
    }
}
