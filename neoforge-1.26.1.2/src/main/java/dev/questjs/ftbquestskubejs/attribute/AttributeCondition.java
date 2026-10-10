package dev.questjs.ftbquestskubejs.attribute;

/**
 * A serializable, loader-independent requirement for one player attribute.
 * For RANGE, {@code value} is the inclusive lower bound and
 * {@code upperValue} is the inclusive upper bound.
 */
public record AttributeCondition(
        String attributeId,
        ComparisonOperator comparison,
        double value,
        double upperValue
) {
    public boolean isValid() {
        if (attributeId == null || attributeId.isBlank() || !attributeId.equals(attributeId.trim())
                || comparison == null || !Double.isFinite(value)) {
            return false;
        }

        return comparison != ComparisonOperator.RANGE
                || (Double.isFinite(upperValue) && value <= upperValue);
    }

    public boolean matches(double actual) {
        return isValid() && comparison.matches(actual, value, upperValue);
    }
}
