package rocks.inspectit.ocelot.eum.server.configuration.model.metrics.definition.view;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum AggregationType {

    LAST_VALUE("Last value"),
    SUM("Sum"),
    HISTOGRAM("Explicit histogram"),
    EXPONENTIAL_HISTOGRAM("Exponential histogram"),

    // Calculate percentiles with raw values
    QUANTILES("Quantiles"),
    // Dropping lower or higher values for average
    SMOOTHED_AVERAGE("Smoothed average");

    @Getter
    private final String readableName;

    /**
     * @return true, if this is a OpenTelemetry aggregation
     */
    public boolean isOpenTelemetryAggregation() {
        return this.equals(LAST_VALUE) || this.equals(SUM) || this.equals(HISTOGRAM) || this.equals(EXPONENTIAL_HISTOGRAM);
    }

    /**
     * @return true, if this is a custom time-window aggregation
     */
    public boolean isTimeWindowAggregation() {
        return this.equals(QUANTILES) || this.equals(SMOOTHED_AVERAGE);
    }
}
