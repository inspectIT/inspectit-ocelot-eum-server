package rocks.inspectit.ocelot.eum.server.metrics;

import com.google.common.annotations.VisibleForTesting;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.context.Scope;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import rocks.inspectit.ocelot.eum.server.arithmetic.RawExpression;
import rocks.inspectit.ocelot.eum.server.beacon.Beacon;
import rocks.inspectit.ocelot.eum.server.beacon.recorder.BeaconRecorder;
import rocks.inspectit.ocelot.eum.server.configuration.model.metric.definition.BeaconRequirement;
import rocks.inspectit.ocelot.eum.server.configuration.model.tags.BeaconTagSettings;
import rocks.inspectit.ocelot.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.ocelot.eum.server.configuration.model.metric.definition.BeaconMetricDefinitionSettings;
import rocks.inspectit.ocelot.eum.server.events.RegisteredAttributesEvent;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Central component, which is responsible for writing beacon entries as OpenTelemetry instruments.
 */
@Component
@Slf4j
public class BeaconMetricManager {

    @Autowired
    protected EumServerConfiguration configuration;

    @Autowired
    private InstrumentManager instrumentManager;

    @Autowired(required = false)
    private List<BeaconRecorder> beaconRecorders;

    /**
     * Set of all registered beacon attributes
     */
    @VisibleForTesting
    Set<String> registeredBeaconAttributes = Collections.emptySet();

    /**
     * Maps metric definitions to expressions.
     */
    private final Map<BeaconMetricDefinitionSettings, RawExpression> expressionCache = new HashMap<>();

    @EventListener
    public void processUsedAttributes(RegisteredAttributesEvent registeredAttributesEvent) {
        Map<String, BeaconTagSettings> beaconAttributeSettings = configuration.getAttributes().getBeacon();

        registeredBeaconAttributes = registeredAttributesEvent.getRegisteredAttributes()
                .stream()
                .filter(beaconAttributeSettings::containsKey)
                .collect(Collectors.toSet());
    }

    @PostConstruct
    void initMetrics() {
        Map<String, BeaconMetricDefinitionSettings> definitions = configuration.getDefinitions();
        for (Map.Entry<String, BeaconMetricDefinitionSettings> metricDefinitionEntry : definitions.entrySet()) {
            String metricName = metricDefinitionEntry.getKey();
            BeaconMetricDefinitionSettings metricDefinition = metricDefinitionEntry.getValue();

            log.debug("Registering beacon metric: {}", metricName);
            instrumentManager.updateInstruments(metricName, metricDefinition);
        }
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

        // allow each beacon recorder to record stuff
        if (!CollectionUtils.isEmpty(beaconRecorders)) {
            try (Scope scope = getBaggageForBeacon(beacon).makeCurrent()) {
                beaconRecorders.forEach(beaconRecorder -> beaconRecorder.record(beacon));
            }
        }

        return successful;
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
    private void recordMetric(String metricName, BeaconMetricDefinitionSettings metricDefinition, Beacon beacon) {
        RawExpression expression = expressionCache.computeIfAbsent(metricDefinition, definition -> new RawExpression(definition
                .getValueExpression()));

        if (expression.isSolvable(beacon)) {
            Number value = expression.solve(beacon);

            if (value != null) {
                try (Scope scope = getBaggageForBeacon(beacon).makeCurrent()) {
                    instrumentManager.recordInstrument(metricName, metricDefinition, value);
                }
            }
        }
    }

    /**
     * Builds baggage for a given beacon.
     *
     * @param beacon Used to resolve baggage values, which refer to a beacon entry
     *
     * @return the
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
