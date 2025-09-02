package rocks.inspectit.ocelot.eum.server.configuration.model.exporters.metrics;

import lombok.Data;
import lombok.NoArgsConstructor;
import rocks.inspectit.ocelot.eum.server.configuration.model.exporters.ExporterEnabledState;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Settings for the Prometheus metrics exporter.
 */
@Data
@NoArgsConstructor
public class PrometheusExporterSettings {

    /**
     * Whether the exporter should be started.
     */
    private ExporterEnabledState enabled;

    /**
     * The hostname on which the /metrics endpoint of prometheus will be started.
     */
    @NotBlank
    private String host;

    /**
     * The port on which the /metrics endpoint of prometheus will be started.
     */
    @Min(1)
    @Max(65535)
    private int port;
}
