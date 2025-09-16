package rocks.inspectit.ocelot.eum.server.metrics;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.*;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import rocks.inspectit.ocelot.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.ocelot.eum.server.configuration.model.metrics.definition.MetricDefinitionSettings;
import rocks.inspectit.ocelot.eum.server.metrics.timewindow.worker.TimeWindowRecorder;
import rocks.inspectit.ocelot.eum.server.utils.AttributeUtil;

import java.util.*;

/**
 * Central component, which is responsible for writing communication with the OpenTelemetry instruments.
 * Note: The EUM-server cannot update metric definitions during runtime.
 */
@Component
@Slf4j
public class InstrumentManager {

    @Autowired
    private EumServerConfiguration configuration;

    @Autowired
    private InstrumentFactory instrumentFactory;

    @Autowired
    private AttributesRegistry attributesRegistry;

    @Autowired
    private TimeWindowRecorder timeWindowRecorder;

    /**
     * Created OpenTelemetry instruments referenced by their metric name.
     * Since {@code AbstractInstrument} is package-private, we store instruments as {@code Object}
     * and cast them to proper data types during recording.
     */
    private final Map<String, Object> instruments = new HashMap<>();

    /**
     * Creates the instrument in {@link #instruments} if necessary.
     *
     * @param metricName the metric name
     * @param metricDefinition the configuration for the metric
     */
    public void createInstrument(String metricName, MetricDefinitionSettings metricDefinition) {
        if (!isInstrumentRegistered(metricName)) {
            MetricDefinitionSettings populatedMetricDefinition = metricDefinition.getCopyWithDefaultsPopulated(metricName);

            if (shouldCreateInstrument(metricDefinition)) {
                Object instrument = instrumentFactory.createInstrument(metricName, populatedMetricDefinition);
                instruments.put(metricName, instrument);
            }

            val views = populatedMetricDefinition.getViews();
            if(!CollectionUtils.isEmpty(views)) {
                views.values().forEach(view -> attributesRegistry.processAttributeKeysForView(view));
            }
        }
    }

    /**
     * Records a value for the metric via OpenTelemetry {@link Meter} or/and via {@link TimeWindowRecorder}.
     *
     * @param metricName        the name of the metric
     * @param metricDefinition the configuration of the metric, which is activated
     * @param value            the value, which is going to be written
     */
    public void recordMetric(String metricName, MetricDefinitionSettings metricDefinition, Number value) {
        if (log.isDebugEnabled())
            log.debug("Recording metric '{}' with value '{}'", metricName, value);

        Attributes attributes = AttributeUtil.toAttributes(Baggage.current());

        if (isInstrumentRegistered(metricName)) {
            switch (metricDefinition.getInstrumentType()) {
                case COUNTER -> recordCounter(metricName, metricDefinition, value, attributes);
                case UP_DOWN_COUNTER -> recordUpDownCounter(metricName, metricDefinition, value, attributes);
                case GAUGE -> recordGauge(metricName, metricDefinition, value, attributes);
                case HISTOGRAM -> recordHistogram(metricName, metricDefinition, value, attributes);
                default -> throw new IllegalArgumentException("Tried to record unsupported instrument type: " + metricDefinition.getInstrumentType().name());
            }
        }

        timeWindowRecorder.recordMetric(metricName, value.doubleValue(), Baggage.current());
    }

    private void recordCounter(String instrumentName, MetricDefinitionSettings metricDefinition, Number value, Attributes attributes) {
        switch (metricDefinition.getValueType()) {
            case LONG -> {
                LongCounter counter = (LongCounter) instruments.get(instrumentName);
                counter.add(value.longValue(), attributes);
            }
            case DOUBLE -> {
                DoubleCounter counter = (DoubleCounter) instruments.get(instrumentName);
                counter.add(value.doubleValue(), attributes);
            }
        }
    }

    private void recordUpDownCounter(String instrumentName, MetricDefinitionSettings metricDefinition, Number value, Attributes attributes) {
        switch (metricDefinition.getValueType()) {
            case LONG -> {
                LongUpDownCounter counter = (LongUpDownCounter) instruments.get(instrumentName);
                counter.add(value.longValue(), attributes);
            }
            case DOUBLE -> {
                DoubleUpDownCounter counter = (DoubleUpDownCounter) instruments.get(instrumentName);
                counter.add(value.doubleValue(), attributes);
            }
        }
    }

    private void recordGauge(String instrumentName, MetricDefinitionSettings metricDefinition, Number value, Attributes attributes) {
        switch (metricDefinition.getValueType()) {
            case LONG -> {
                LongGauge counter = (LongGauge) instruments.get(instrumentName);
                counter.set(value.longValue(), attributes);
            }
            case DOUBLE -> {
                DoubleGauge counter = (DoubleGauge) instruments.get(instrumentName);
                counter.set(value.doubleValue(), attributes);
            }
        }
    }

    private void recordHistogram(String instrumentName, MetricDefinitionSettings metricDefinition, Number value, Attributes attributes) {
        switch (metricDefinition.getValueType()) {
            case LONG -> {
                LongHistogram counter = (LongHistogram) instruments.get(instrumentName);
                counter.record(value.longValue(), attributes);
            }
            case DOUBLE -> {
                DoubleHistogram counter = (DoubleHistogram) instruments.get(instrumentName);
                counter.record(value.doubleValue(), attributes);
            }
        }
    }

    /**
     * Checks, if we should create an instrument for the metric. We only need an instrument, if the metric definition
     * contains a view, which uses an OpenTelemetry aggregation <b>OR</b>
     * if there are no views specified at all. Then we will use the default OpenTelemetry views,
     * which OpenTelemetry handles by itself automatically.
     * For time-window aggregations we will record metrics via {@link TimeWindowRecorder} later.
     *
     * @param metricDefinition the metric definition
     *
     * @return true, if we should create an instrument
     */
    private boolean shouldCreateInstrument(MetricDefinitionSettings metricDefinition) {
        boolean useDefaultView = CollectionUtils.isEmpty(metricDefinition.getViews());
        return useDefaultView || metricDefinition.getViews()
                .values().stream()
                .anyMatch(view -> view.getAggregation().isOpenTelemetryAggregation());
    }

    /**
     * @param metricName the name of the metric
     *
     * @return true, if an OpenTelemetry instrument was created for the metric
     */
    private boolean isInstrumentRegistered(String metricName) {
        return instruments.containsKey(metricName);
    }

    /**
     * Builds baggage with all global extra attributes.
     */
    public Baggage getBaggage() {
        BaggageBuilder builder = Baggage.current().toBuilder();
        Set<String> registeredGlobalAttributes = attributesRegistry.getRegisteredGlobalAttributes();

        for (String registeredGlobalAttribute : registeredGlobalAttributes) {
            builder.put(registeredGlobalAttribute, configuration
                    .getAttributes()
                    .getExtra()
                    .get(registeredGlobalAttribute));
        }
        return builder.build();
    }

    /**
     * Builds baggage with all custom attributes.
     *
     * @param customAttributes Map containing the custom attributes.
     *
     * @return {@link Baggage} which contains the custom and global (extra) attributes
     */
    public Baggage getBaggage(Map<String, String> customAttributes) {
        BaggageBuilder builder = getBaggage().toBuilder();

        for (Map.Entry<String, String> customAttribute : customAttributes.entrySet()) {
            builder.put(customAttribute.getKey(), customAttribute.getValue());
        }

        return builder.build();
    }
}
