package rocks.inspectit.oce.eum.server.configuration.model.selfmonitoring;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import org.springframework.validation.annotation.Validated;
import rocks.inspectit.oce.eum.server.configuration.model.metric.definition.MetricDefinitionSettings;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * Self-monitoring settings.
 */
@Data
@Validated
public class EumSelfMonitoringSettings{

    /**
     * If self-monitoring is enabled.
     */
    private boolean enabled;

    /**
     * Definition of the self-monitoring metrics.
     */
    @Singular
    private Map<@NotBlank String, @Valid @NotNull MetricDefinitionSettings> metrics;

    /**
     * The prefix used for the self-monitoring metrics.
     */
    private String metricPrefix;
}
