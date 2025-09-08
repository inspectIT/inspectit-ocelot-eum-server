package rocks.inspectit.ocelot.eum.server.configuration.model.metric.definition.view;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum AggregationType {

    LAST_VALUE("last value"),
    SUM("sum"),
    HISTOGRAM("explicit histogram"),
    EXPONENTIAL_HISTOGRAM("exponential histogram"),

    // Calculate percentiles
    QUANTILES("quantiles"),
    // Dropping lower or higher values for average
    SMOOTHED_AVERAGE("smoothed average");

    @Getter
    private final String readableName;
}
