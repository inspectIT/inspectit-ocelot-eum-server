package rocks.inspectit.ocelot.eum.server.configuration.model;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import rocks.inspectit.ocelot.eum.server.configuration.model.exporters.ExportersSettings;
import rocks.inspectit.ocelot.eum.server.configuration.model.metric.definition.BeaconMetricDefinitionSettings;
import rocks.inspectit.ocelot.eum.server.configuration.model.security.SecuritySettings;
import rocks.inspectit.ocelot.eum.server.configuration.model.selfmonitoring.SelfMonitoringSettings;
import rocks.inspectit.ocelot.eum.server.configuration.model.attributes.AttributeSettings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.Map;

/**
 * The configuration of the EUM server
 */
@ConfigurationProperties("inspectit-eum-server")
@Component
@Data
@Validated
public class EumServerConfiguration {

    /**
     * List of metric definitions
     */
    @Valid
    @NotNull
    private Map<@NotBlank String, @NotNull @Valid BeaconMetricDefinitionSettings> definitions = Collections.emptyMap();

    /**
     * Map of attributes
     */
    @Valid
    private AttributeSettings attributes;

    /**
     * Self Monitoring
     */
    @Valid
    private SelfMonitoringSettings selfMonitoring;

    /**
     * The exporters settings
     */
    @Valid
    private ExportersSettings exporters;

    /**
     * The security settings for the REST API
     */
    @Valid
    private SecuritySettings security;
}
