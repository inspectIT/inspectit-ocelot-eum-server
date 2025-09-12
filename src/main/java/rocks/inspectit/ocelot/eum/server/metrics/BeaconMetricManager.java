package rocks.inspectit.ocelot.eum.server.metrics;

import com.google.common.annotations.VisibleForTesting;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.context.Scope;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import rocks.inspectit.ocelot.eum.server.arithmetic.RawExpression;
import rocks.inspectit.ocelot.eum.server.beacon.Beacon;
import rocks.inspectit.ocelot.eum.server.beacon.recorder.BeaconRecorder;
import rocks.inspectit.ocelot.eum.server.beacon.recorder.ResourceTimingBeaconRecorder;
import rocks.inspectit.ocelot.eum.server.configuration.model.metric.definition.BeaconRequirement;
import rocks.inspectit.ocelot.eum.server.configuration.model.attributes.BeaconAttributeSettings;
import rocks.inspectit.ocelot.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.ocelot.eum.server.configuration.model.metric.definition.BeaconMetricDefinitionSettings;
import rocks.inspectit.ocelot.eum.server.events.RegisteredAttributesEvent;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Central component, which is responsible for writing beacon entries as OpenTelemetry metrics.
 */
@Component
@Slf4j
public class BeaconMetricManager {

    @Autowired
    protected EumServerConfiguration configuration;

    @Autowired
    private InstrumentManager instrumentManager;

    @Autowired
    private SelfMonitoringMetricManager selfMonitoringMetricManager;

    /**
     * Currently just {@link ResourceTimingBeaconRecorder}
     */
    @Autowired(required = false)
    private List<BeaconRecorder> beaconRecorders;

    /**
     * Set of all registered beacon attributes
     */
    @VisibleForTesting
    Set<String> registeredBeaconAttributes = Collections.emptySet();

    /**
     * Maps metric definitions to expressions
     */
    private final Map<BeaconMetricDefinitionSettings, RawExpression> expressionCache = new HashMap<>();

    /**
     * We listen to the {@link ApplicationStartedEvent}, because {@link PostConstruct} is too early to receive
     * {@link RegisteredAttributesEvent}s
     */
    @EventListener(ApplicationStartedEvent.class)
    void initMetrics() {
        Map<String, BeaconMetricDefinitionSettings> definitions = configuration.getDefinitions();
        for (Map.Entry<String, BeaconMetricDefinitionSettings> metricDefinitionEntry : definitions.entrySet()) {
            String metricName = metricDefinitionEntry.getKey();
            BeaconMetricDefinitionSettings metricDefinition = metricDefinitionEntry.getValue();

            log.debug("Registering beacon metric: {}", metricName);
            instrumentManager.createInstrument(metricName, metricDefinition);
        }
        // Initialize self-monitoring metrics after beacon metrics
        selfMonitoringMetricManager.initMetrics();
        log.info("Registration of metrics completed");
    }

    // TODO Refactor this
    @EventListener
    void processUsedAttributes(RegisteredAttributesEvent registeredAttributesEvent) {
        Map<String, BeaconAttributeSettings> beaconAttributeSettings = configuration.getAttributes().getBeacon();

        registeredBeaconAttributes = registeredAttributesEvent.getRegisteredAttributes()
                .stream()
                .filter(beaconAttributeSettings::containsKey)
                .collect(Collectors.toSet());
    }

    /**
     * Processes boomerang beacon.
     *
     * @param beacon The beacon containing arbitrary key-value pairs.
     *
     * @return whether the beacon has been successfully parsed
     */
    public boolean processBeacon(Beacon beacon) {
        boolean successful = false;

        Map<String, BeaconMetricDefinitionSettings> definitions = configuration.getDefinitions();
        if (CollectionUtils.isEmpty(definitions)) {
            successful = true;
        } else {
            for (Map.Entry<String, BeaconMetricDefinitionSettings> metricDefinitionEntry : definitions.entrySet()) {
                String metricName = metricDefinitionEntry.getKey();
                BeaconMetricDefinitionSettings metricDefinition = metricDefinitionEntry.getValue();

                if (BeaconRequirement.validate(beacon, metricDefinition.getBeaconRequirements())) {
                    recordMetric(metricName, metricDefinition, beacon);
                    successful = true;
                } else {
                    log.debug("Skipping beacon because requirements are not fulfilled");
                }
            }
        }

        return successful;
    }

    /**
     * Records the metric via {@link BeaconRecorder} or directly.
     *
     * @param metricName the current metric name
     * @param metricDefinition the current metric definition
     * @param beacon the entire beacon
     */
    private void recordMetric(String metricName, BeaconMetricDefinitionSettings metricDefinition, Beacon beacon) {
        boolean recorded = recordWithBeaconRecorder(metricName, beacon);

        if(!recorded) recordBeaconMetric(metricName, metricDefinition, beacon);
    }

    /**
     * Tries to record the metric with a {@link BeaconRecorder}. Return true, if a fitting recorder was found.
     *
     * @param metricName the current metric name
     * @param beacon the entire beacon
     *
     * @return true, if the metric was recorder via {@link BeaconRecorder}
     */
    private boolean recordWithBeaconRecorder(String metricName, Beacon beacon) {
        if (!CollectionUtils.isEmpty(beaconRecorders)) {
            for (BeaconRecorder recorder : beaconRecorders) {
                if (recorder.canRecord(metricName)) {
                    try (Scope scope = getBaggageForBeacon(beacon).makeCurrent()) {
                        recorder.record(beacon);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Extracts the metric value from the given beacon according to the specified metric definition.
     * In case the metric definition's value expression is not solvable using the given beacon (not all required
     * fields are existing) nothing is done.
     *
     * @param metricName       the metric name
     * @param metricDefinition the metric's definition
     * @param beacon           the current beacon
     */
    private void recordBeaconMetric(String metricName, BeaconMetricDefinitionSettings metricDefinition, Beacon beacon) {
        RawExpression expression = expressionCache.computeIfAbsent(metricDefinition, definition -> new RawExpression(definition
                .getValueExpression()));

        if (expression.isSolvable(beacon)) {
            Number value = expression.solve(beacon);

            if (value != null) {
                try (Scope scope = getBaggageForBeacon(beacon).makeCurrent()) {
                    instrumentManager.recordMetric(metricName, metricDefinition, value);
                }
            }
        }
    }

    /**
     * Builds baggage for a given beacon.
     *
     * @param beacon Used to resolve baggage values, which refer to a beacon entry
     *
     * @return the baggage for the provided beacon
     */
    private Baggage getBaggageForBeacon(Beacon beacon) {
        BaggageBuilder builder = instrumentManager.getBaggage().toBuilder();
        for (String key : registeredBeaconAttributes) {
            if (beacon.contains(key)) {
                builder.put(key, beacon.get(key));
            }
        }
        return builder.build();
    }
}
