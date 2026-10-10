package dev.questjs.ftbquestskubejs.attribute;

/** Numeric comparisons supported by an attribute condition. */
public enum ComparisonOperator {
    GREATER_THAN,
    GREATER_THAN_OR_EQUAL,
    LESS_THAN,
    LESS_THAN_OR_EQUAL,
    EQUAL,
    RANGE;

    public boolean matches(double actual, double value, double upperValue) {
        if (!Double.isFinite(actual) || !Double.isFinite(value)) {
            return false;
        }

        return switch (this) {
            case GREATER_THAN -> actual > value;
            case GREATER_THAN_OR_EQUAL -> actual >= value;
            case LESS_THAN -> actual < value;
            case LESS_THAN_OR_EQUAL -> actual <= value;
            case EQUAL -> actual == value;
            case RANGE -> Double.isFinite(upperValue) && actual >= value && actual <= upperValue;
        };
    }
}
