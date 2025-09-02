package rocks.inspectit.ocelot.eum.server.configuration.model.exporters.beacon;

import lombok.Data;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;

/**
 * Beacon exporter settings.
 */
@Data
@Validated
public class BeaconExporterSettings {

    /**
     * Settings for exporting beacons via HTTP.
     */
    @Valid
    private BeaconHttpExporterSettings http;

}
