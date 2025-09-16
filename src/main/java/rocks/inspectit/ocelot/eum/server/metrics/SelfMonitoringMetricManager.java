package rocks.inspectit.ocelot.eum.server.metrics;

import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rocks.inspectit.ocelot.eum.server.configuration.model.metrics.definition.MetricDefinitionSettings;
import rocks.inspectit.ocelot.eum.server.configuration.model.selfmonitoring.SelfMonitoringSettings;
import rocks.inspectit.ocelot.eum.server.configuration.model.EumServerConfiguration;

import java.util.Collections;
import java.util.Map;

/**
 * Central component, which is responsible for recording self monitoring metrics.
 */
@Component
@Slf4j
public class SelfMonitoringMetricManager {

    @Autowired
    private EumServerConfiguration configuration;

    @Autowired
    private InstrumentManager instrumentManager;

    /**
     * Initialize self-monitoring metrics
     */
    public void initMetrics() {
        SelfMonitoringSettings selfMonitoringSettings = configuration.getSelfMonitoring();
        for (Map.Entry<String, MetricDefinitionSettings> metricEntry : selfMonitoringSettings.getMetrics().entrySet()) {
            String measureName = metricEntry.getKey();
            MetricDefinitionSettings metricDefinitionSettings = metricEntry.getValue();

            String metricName = selfMonitoringSettings.getMetricPrefix() + measureName;
            log.info("Registering self-monitoring metric: {}", metricName);

            instrumentManager.createInstrument(metricName, metricDefinitionSettings);
        }
    }

    /**
     * Records a self-monitoring measurement with the common attributes.
     * Only records a measurement if self monitoring is enabled.
     *
     * @param measureName the name of the measure, excluding the metrics prefix
     * @param value       the actual value
     * @param customAttributes  custom attributes
     */
    public void record(String measureName, Number value, Map<String, String> customAttributes) {
        SelfMonitoringSettings selfMonitoringSettings = configuration.getSelfMonitoring();
        if (selfMonitoringSettings.isEnabled() && selfMonitoringSettings.getMetrics().containsKey(measureName)) {
            MetricDefinitionSettings metricDefinitionSettings = selfMonitoringSettings.getMetrics().get(measureName);

            String metricName = selfMonitoringSettings.getMetricPrefix() + measureName;

            try (Scope scope = instrumentManager.getBaggage(customAttributes).makeCurrent()) {
                instrumentManager.recordMetric(metricName, metricDefinitionSettings, value);
            }
        }
    }

    /**
     * Records a self-monitoring measurement with the common attributes.
     * Only records a measurement if self monitoring is enabled.
     *
     * @param measureName the name of the measure, excluding the metrics prefix
     * @param value       the actual value
     */
    public void record(String measureName, Number value) {
        record(measureName, value, Collections.emptyMap());
    }
}
