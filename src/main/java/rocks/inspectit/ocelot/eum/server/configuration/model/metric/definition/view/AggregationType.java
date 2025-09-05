package rocks.inspectit.ocelot.eum.server.configuration.model.metric.definition.view;

import io.opentelemetry.sdk.metrics.Aggregation;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum AggregationType {

    LAST_VALUE("last value", Aggregation.lastValue()),
    SUM("sum", Aggregation.sum()),
    HISTOGRAM("histogram", Aggregation.explicitBucketHistogram()),

    // TODO Don't know what to do with custom aggregations in the model yet
    // Calculate Percentiles
    QUANTILES("quantiles", Aggregation.defaultAggregation()),
    // Dropping lower or higher values for average
    SMOOTHED_AVERAGE("smoothed average", Aggregation.defaultAggregation());

    @Getter
    private final String readableName;

    private final Aggregation aggregation;

    public Aggregation convert() {
        return aggregation;
    }
}
