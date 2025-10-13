package rocks.inspectit.ocelot.eum.server.configuration.model.selfmonitoring;

import lombok.Data;
import lombok.Singular;
import org.springframework.validation.annotation.Validated;
import rocks.inspectit.ocelot.eum.server.configuration.model.metrics.definition.MetricDefinitionSettings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import rocks.inspectit.ocelot.eum.server.configuration.model.metrics.definition.view.ViewDefinitionSettings;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Self-monitoring settings.
 */
@Data
@Validated
public class SelfMonitoringSettings {

    /**
     * If self-monitoring is enabled.
     */
    private boolean enabled;

    /**
     * Definition of the self-monitoring metrics.
     */
    @Singular
    private Map<@NotBlank String, @Valid @NotNull MetricDefinitionSettings> metrics = Collections.emptyMap();

    /**
     * The prefix used for the self-monitoring metrics.
     */
    private String metricPrefix;

    /**
     * Marker to tell if the {@link #metricPrefix} has been applied to views
     */
    private boolean prefixNotApplied = true;

    /**
     * Adds the {@link #metricPrefix} for all view names. The prefix to the metrics has to be added manually.
     *
     * @return the metrics with prefixed views
     */
    public Map<String, MetricDefinitionSettings> getMetricsWithPrefixedViews() {
        if (prefixNotApplied) {
            metrics.forEach((metricName, metricDefinition) -> {

                Map<String, ViewDefinitionSettings> prefixedViews = new LinkedHashMap<>();
                metricDefinition.getViews()
                        .forEach((viewName, viewDefinition) ->
                                prefixedViews.put(metricPrefix + viewName, viewDefinition)
                        );
                metricDefinition.setViews(prefixedViews);

            });
            prefixNotApplied = false;
        }
        return metrics;
    }
}
