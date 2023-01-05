package rocks.inspectit.oce.eum.server.configuration.model.exporters.trace;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.time.DurationMin;
import rocks.inspectit.oce.eum.server.configuration.model.CompressionMethod;
import rocks.inspectit.oce.eum.server.configuration.model.exporters.ExporterEnabledState;
import rocks.inspectit.oce.eum.server.configuration.model.exporters.TransportProtocol;

import java.time.Duration;
import java.util.Map;

/**
 * Settings for {@link rocks.inspectit.oce.eum.server.exporters.configuration.TraceExportersConfiguration}
 */
@Data
@NoArgsConstructor
public class OtlpTraceExporterSettings {

    private ExporterEnabledState enabled;

    /**
     * The OTLP traces endpoint to connect to.
     */
    private String endpoint;

    /**
     * The transport protocol to use.
     * Supported protocols are {@link TransportProtocol#GRPC} and {@link TransportProtocol#HTTP_PROTOBUF}
     */
    private TransportProtocol protocol;

    /**
     * Key-value pairs to be used as headers associated with gRPC or HTTP requests.
     */
    private Map<String, String> headers;

    /**
     * The compression method.
     */
    private CompressionMethod compression;

    /**
     * Maximum time the OTLP exporter will wait for each batch export.
     */
    @DurationMin(millis = 1)
    private Duration timeout;

}
