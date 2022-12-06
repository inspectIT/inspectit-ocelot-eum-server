package rocks.inspectit.oce.eum.server.configuration.model.exporters.trace;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.time.DurationMin;
import rocks.inspectit.oce.eum.server.configuration.model.CompressionMethod;
import rocks.inspectit.oce.eum.server.configuration.model.exporters.ExporterEnabledState;
import rocks.inspectit.oce.eum.server.configuration.model.exporters.TransportProtocol;

import java.time.Duration;

@Data
@NoArgsConstructor
public class JaegerExporterSettings {

    /**
     * Whether the exporter should be started.
     */
    private ExporterEnabledState enabled;

    @Deprecated
    /**
     * This property is deprecated since v2.0. Please use {@link #endpoint} instead.
     * The gRPC endpoint of the Jaeger server.
     */ private String grpc;

    @Deprecated
    /**
     * This property is deprecated since v2.0. Please use {@link #endpoint} instead.
     * The URL of the Jaeger server.
     */ private String url;

    /**
     * The URL endpoint of the Jaeger server.
     */
    private String endpoint;

    /**
     * The transport protocol to use.
     * Supported protocols are {@link TransportProtocol#HTTP_THRIFT} and {@link TransportProtocol#GRPC}
     */
    private TransportProtocol protocol;

    @Deprecated
    /**
     * This property is deprecated in favor of {@link TraceExportersSettings#serviceName}.
     * The service name. Used in {@link rocks.inspectit.oce.eum.server.exporters.configuration.TraceExportersConfiguration}
     */
    private String serviceName;

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
