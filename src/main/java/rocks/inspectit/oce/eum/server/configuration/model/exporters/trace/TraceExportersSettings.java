package rocks.inspectit.oce.eum.server.configuration.model.exporters.trace;

import lombok.Data;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;

@Data
@Validated
public class TraceExportersSettings {

    @Valid
    private OtlpTraceExporterSettings otlp;

    /**
     * Specifies whether client IP addresses which are added to spans should be masked.
     */
    private boolean maskSpanIpAddresses;

    /**
     * The service name. Used in {@link rocks.inspectit.oce.eum.server.exporters.configuration.TraceExportersConfiguration}
     */
    private String serviceName;
}
