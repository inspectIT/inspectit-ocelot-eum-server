package rocks.inspectit.oce.eum.server.configuration.model.selfmonitoring;

import lombok.Data;
import lombok.Singular;
import org.springframework.validation.annotation.Validated;
import rocks.inspectit.oce.eum.server.configuration.model.metric.definition.MetricDefinitionSettings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
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
}
