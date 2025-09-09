package rocks.inspectit.ocelot.eum.server.metrics;

import com.google.common.annotations.VisibleForTesting;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import rocks.inspectit.ocelot.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.ocelot.eum.server.configuration.model.metric.definition.MetricDefinitionSettings;
import rocks.inspectit.ocelot.eum.server.configuration.model.metric.definition.view.ViewDefinitionSettings;
import rocks.inspectit.ocelot.eum.server.events.RegisteredAttributesEvent;
//import rocks.inspectit.ocelot.eum.server.metrics.percentiles.TimeWindowViewManager;
import rocks.inspectit.ocelot.eum.server.utils.AttributeUtil;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Central component, which is responsible for writing communication with the OpenTelemetry.
 */
@Component
@Slf4j
public class InstrumentManager {

    // TODO Check out: https://github.com/inspectIT/inspectit-ocelot/tree/feature/feat-1571-opentelemetry-migration-replace-metrics

    @Autowired
    private EumServerConfiguration configuration;

//    @Autowired
//    private TimeWindowViewManager timeWindowViewManager;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private InstrumentFactory instrumentFactory;

    /**
     * Created OpenTelemetry instruments.
     * Since {@code AbstractInstrument} is package-private, we store instruments as {@code Object}
     * and cast them to proper data types during runtime.
     */
    private final Map<String, Object> instruments = new HashMap<>();

    /**
     * Set of all registered attributes
     */
    private final Set<String> registeredAttributes = new HashSet<>();

    /**
     * Set of all registered global attributes
     */
    @VisibleForTesting
    Set<String> registeredGlobalAttributes = Collections.emptySet();

    /**
     * Creates or updates the instruments in {@link #instruments}.
     *
     * @param name the metric name
     * @param metricDefinition the configuration for the metric
     */
    public void updateInstruments(String name, MetricDefinitionSettings metricDefinition) {
        if (!instruments.containsKey(name)) {
            MetricDefinitionSettings populatedMetricDefinition = metricDefinition.getCopyWithDefaultsPopulated(name, Duration
                    .ofSeconds(15)); // Default value of 15s will be overridden by configuration.
            Object instrument = instrumentFactory.createInstrument(name, populatedMetricDefinition);
            instruments.put(name, instrument);
            //updateViews(name, populatedMetricDefinition);
            populatedMetricDefinition.getViews().values().forEach(this::processAttributeKeysForView);
        }
    }

    /**
     * Records a value for the instrument.
     *
     * @param instrumentName   the name of the instrument
     * @param metricDefinition the configuration of the metric, which is activated
     * @param value            the value, which is going to be written.
     */
    public void recordInstrument(String instrumentName, MetricDefinitionSettings metricDefinition, Number value) {
        if (log.isDebugEnabled()) {
            log.debug("Recording instrument '{}' with value '{}'.", instrumentName, value);
        }

        Attributes attributes = AttributeUtil.toAttributes(Baggage.current());

        switch (metricDefinition.getInstrumentType()) {
            case COUNTER -> recordCounter(instrumentName, metricDefinition, value, attributes);
            case UP_DOWN_COUNTER -> recordUpDownCounter(instrumentName, metricDefinition, value, attributes);
            case GAUGE -> recordGauge(instrumentName, metricDefinition, value, attributes);
            case HISTOGRAM -> recordHistogram(instrumentName, metricDefinition, value, attributes);
            default -> throw new IllegalArgumentException("Tried to record unsupported instrument type:" + metricDefinition.getInstrumentType().name());
        }

        // TODO Refactor percentiles package

//        timeWindowViewManager.recordMeasurement(measureName, value.doubleValue(), Tags.getTagger()
//                .getCurrentTagContext());
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

    // TODO With the ViewManager, do we need to create any views here?

//    /**
//     * Creates a new {@link View}, if a view for the given metricDefinition was not created, yet.
//     *
//     * @param metricDefinition the settings of the metric definition
//     */
//    private void updateViews(String metricName, MetricDefinitionSettings metricDefinition) {
//        for (Map.Entry<String, ViewDefinitionSettings> viewDefinitionSettingsEntry : metricDefinition.getViews()
//                .entrySet()) {
//            String viewName = viewDefinitionSettingsEntry.getKey();
//            ViewDefinitionSettings viewDefinitionSettings = viewDefinitionSettingsEntry.getValue();
//            if (viewManager.getAllExportedViews().stream().noneMatch(v -> v.getName().asString().equals(viewName))) {
//                Measure measure = metrics.get(metricName);
//
//                boolean isRegistered = timeWindowViewManager.isViewRegistered(metricName, viewName);
//                boolean isQuantileAggregation = viewDefinitionSettings.getAggregation() == ViewDefinitionSettings.Aggregation.QUANTILES;
//                boolean isSmoothedAverageAggregation = viewDefinitionSettings.getAggregation() == ViewDefinitionSettings.Aggregation.SMOOTHED_AVERAGE;
//                if (isRegistered || isQuantileAggregation || isSmoothedAverageAggregation) {
//                    addTimeWindowView(measure, viewName, viewDefinitionSettings);
//                } else {
//                    registerNewView(measure, viewName, viewDefinitionSettings);
//                }
//            }
//        }
//    }
//
//    private void addTimeWindowView(Measure measure, String viewName, ViewDefinitionSettings def) {
//        List<TagKey> viewTags = getAttributeKeysForView(def);
//        Set<String> tagsAsStrings = viewTags.stream().map(TagKey::getName).collect(Collectors.toSet());
//        if (def.getAggregation() == ViewDefinitionSettings.Aggregation.QUANTILES) {
//            boolean minEnabled = def.getQuantiles().contains(0.0);
//            boolean maxEnabled = def.getQuantiles().contains(1.0);
//            List<Double> percentilesFiltered = def.getQuantiles()
//                    .stream()
//                    .filter(p -> p > 0 && p < 1)
//                    .collect(Collectors.toList());
//            timeWindowViewManager.createOrUpdatePercentileView(measure.getName(), viewName, measure.getUnit(), def.getDescription(), minEnabled, maxEnabled, percentilesFiltered, def
//                    .getTimeWindow()
//                    .toMillis(), tagsAsStrings, def.getMaxBufferedPoints());
//        } else {
//            timeWindowViewManager.createOrUpdateSmoothedAverageView(measure.getName(), viewName, measure.getUnit(), def.getDescription(), def
//                    .getDropUpper(), def.getDropLower(), def.getTimeWindow()
//                    .toMillis(), tagsAsStrings, def.getMaxBufferedPoints());
//        }
//    }
//
//    private void registerNewView(Measure measure, String viewName, ViewDefinitionSettings def) {
//        Aggregation aggregation = createAggregation(def);
//        List<TagKey> tagKeys = getAttributeKeysForView(def);
//        View view = View.create(View.Name.create(viewName), def.getDescription(), measure, aggregation, tagKeys);
//        viewManager.registerView(view);
//    }
//
//    /**
//     * Returns all tags, which are exposed for the given metricDefinition
//     */
    private void processAttributeKeysForView(ViewDefinitionSettings viewDefinitionSettings) {
        Set<String> attributes = new HashSet<>(configuration.getAttributes().getDefineAsGlobal());
        attributes.addAll(viewDefinitionSettings.getAttributes()
                .entrySet()
                .stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .toList());

        processRegisteredAttributes(attributes);
    }

    /**
     * Publish all registered attributes as event.
     *
     * @param attributes the registered attributes
     */
    @VisibleForTesting
    void processRegisteredAttributes(Set<String> attributes) {
        registeredAttributes.addAll(attributes);

        RegisteredAttributesEvent registeredAttributesEvent = new RegisteredAttributesEvent(this, registeredAttributes);
        applicationEventPublisher.publishEvent(registeredAttributesEvent);

        registeredGlobalAttributes = registeredAttributes.stream()
                .filter(configuration.getAttributes().getExtra()::containsKey)
                .filter(configuration.getAttributes().getDefineAsGlobal()::contains)
                .collect(Collectors.toSet());
    }

    /**
     * Builds baggage with all global extra attributes.
     */
    public Baggage getBaggage() {
        BaggageBuilder builder = Baggage.current().toBuilder();

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
