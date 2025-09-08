package rocks.inspectit.ocelot.eum.server.metrics;

import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rocks.inspectit.ocelot.eum.server.configuration.model.metric.definition.MetricDefinitionSettings;
import rocks.inspectit.ocelot.eum.server.opentelemetry.OpenTelemetryController;

@Component
public class InstrumentFactory {

    @Autowired
    private OpenTelemetryController openTelemetry;

    /**
     * Creates a new instrument. Since {@code AbstractInstrument} is package-private, we return an {@code Object}
     *
     * @param name the instrument name
     * @param metricDefinition the instrument settings
     *
     * @return the created instrument as {@code Object}
     */
    public Object createInstrument(String name, MetricDefinitionSettings metricDefinition) {
        return switch (metricDefinition.getInstrumentType()) {
            case COUNTER -> createCounter(name, metricDefinition);
            case UP_DOWN_COUNTER -> createUpDownCounter(name, metricDefinition);
            case GAUGE -> createGauge(name, metricDefinition);
            case HISTOGRAM -> createHistogram(name, metricDefinition);
            default -> throw new IllegalArgumentException("Tried to create unsupported instrument type:" + metricDefinition.getInstrumentType().name());
        };
    }

    private Object createCounter(String name, MetricDefinitionSettings metricDefinition) {
        LongCounterBuilder builder = openTelemetry.getMeter()
                .counterBuilder(name)
                .setDescription(metricDefinition.getDescription())
                .setUnit(metricDefinition.getUnit());

        return switch (metricDefinition.getValueType()) {
            case LONG -> builder.build();
            case DOUBLE -> builder.ofDoubles().build();
        };
    }

    private Object createUpDownCounter(String name, MetricDefinitionSettings metricDefinition) {
        LongUpDownCounterBuilder builder = openTelemetry.getMeter()
                .upDownCounterBuilder(name)
                .setDescription(metricDefinition.getDescription())
                .setUnit(metricDefinition.getUnit());

        return switch (metricDefinition.getValueType()) {
            case LONG -> builder.build();
            case DOUBLE -> builder.ofDoubles().build();
        };
    }

    private Object createGauge(String name, MetricDefinitionSettings metricDefinition) {
        DoubleGaugeBuilder builder = openTelemetry.getMeter()
                .gaugeBuilder(name)
                .setDescription(metricDefinition.getDescription())
                .setUnit(metricDefinition.getUnit());

        return switch (metricDefinition.getValueType()) {
            case LONG -> builder.ofLongs().build();
            case DOUBLE -> builder.build();
        };
    }

    private Object createHistogram(String name, MetricDefinitionSettings metricDefinition) {
        DoubleHistogramBuilder builder = openTelemetry.getMeter()
                .histogramBuilder(name)
                .setDescription(metricDefinition.getDescription())
                .setUnit(metricDefinition.getUnit());

        return switch (metricDefinition.getValueType()) {
            case LONG -> builder.ofLongs().build();
            case DOUBLE -> builder.build();
        };
    }
}
