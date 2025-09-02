package rocks.inspectit.ocelot.eum.server.configuration.model.exporters.metrics;

import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;

/**
 * Settings for metrics exporters.
 */
@Data
@NoArgsConstructor
public class MetricsExportersSettings {

    @Valid
    private PrometheusExporterSettings prometheus;

    @Valid
    private OtlpMetricsExporterSettings otlp;
}
