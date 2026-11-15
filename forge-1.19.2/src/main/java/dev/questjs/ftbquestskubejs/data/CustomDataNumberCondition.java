package dev.questjs.ftbquestskubejs.data;

import dev.questjs.ftbquestskubejs.attribute.ComparisonOperator;

/**
 * A single numeric requirement against the player's persistent data.
 * {@code path} is a dot-separated chain of CompoundTag keys, e.g. "shop.balance".
 * For RANGE, {@code value} is the inclusive lower bound and {@code upperValue}
 * the inclusive upper bound. Values are compared as doubles, so negative and
 * fractional numbers work; Long values beyond 2^53 lose precision.
 */
public record CustomDataNumberCondition(
        String path,
        ComparisonOperator comparison,
        double value,
        double upperValue
) {
    public boolean isValid() {
        if (path == null || path.isBlank() || !path.equals(path.trim())
                || comparison == null || !Double.isFinite(value)) {
            return false;
        }

        for (String segment : path.split("\\.", -1)) {
            if (segment.isEmpty()) {
                return false;
            }
        }

        return comparison != ComparisonOperator.RANGE
                || (Double.isFinite(upperValue) && value <= upperValue);
    }

    public boolean matches(double actual) {
        return isValid() && comparison.matches(actual, value, upperValue);
    }
}
