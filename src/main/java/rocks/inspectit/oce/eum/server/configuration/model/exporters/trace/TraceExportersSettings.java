package rocks.inspectit.oce.eum.server.configuration.model.exporters.trace;

import lombok.Data;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;

@Data
@Validated
public class TraceExportersSettings {

    @Valid
    private JaegerExporterSettings jaeger;

    /**
     * Specifies whether client IP addresses which are added to spans should be masked.
     */
    private boolean maskSpanIpAddresses;
}
