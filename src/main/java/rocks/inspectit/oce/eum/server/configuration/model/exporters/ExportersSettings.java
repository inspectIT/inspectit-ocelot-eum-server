package rocks.inspectit.oce.eum.server.configuration.model.exporters;

import lombok.Data;
import org.springframework.validation.annotation.Validated;
import rocks.inspectit.oce.eum.server.configuration.model.exporters.beacon.BeaconExporterSettings;
import rocks.inspectit.oce.eum.server.configuration.model.exporters.metrics.MetricsExportersSettings;
import rocks.inspectit.oce.eum.server.configuration.model.exporters.trace.TraceExportersSettings;

import jakarta.validation.Valid;

/**
 * Extended exporter settings.
 */
@Data
@Validated
public class ExportersSettings {

    /**
     * Exporter settings for beacon exporters.
     */
    private BeaconExporterSettings beacons;

    /**
     * Exporter settings for metric exporters.
     */
    @Valid
    private MetricsExportersSettings metrics;

    /**
     * Exporter settings for trace exporters.
     */
    @Valid
    private TraceExportersSettings tracing;
}
