package rocks.inspectit.oce.eum.server.exporters.tracing;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import rocks.inspectit.oce.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.oce.eum.server.configuration.model.exporters.trace.OtlpTraceExporterSettings;
import rocks.inspectit.oce.eum.server.opentelemetry.OpenTelemetryController;

import java.util.Map;

@Configuration
@Slf4j
public class TraceExportersConfiguration {

    @Autowired
    private EumServerConfiguration configuration;

    @Autowired
    private OpenTelemetryController openTelemetryController;

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty({"inspectit-eum-server.exporters.tracing.otlp.enabled", "inspectit-eum-server.exporters.tracing.otlp.endpoint"})
    @ConditionalOnExpression("(NOT new String('${inspectit-eum-server.exporters.tracing.otlp.enabled}').toUpperCase().equals(T(rocks.inspectit.oce.eum.server.configuration.model.exporters.ExporterEnabledState).DISABLED.toString())) AND (new String('${inspectit-eum-server.exporters.tracing.otlp.endpoint}').length() > 0)")
    public SpanExporter otlpSpanExporter() {
        SpanExporter spanExporter = null;
        OtlpTraceExporterSettings otlpTraceExporterSettings = configuration.getExporters().getTracing().getOtlp();

        String endpoint = otlpTraceExporterSettings.getEndpoint();
        // OTEL expects that the URI starts with 'http://' or 'https://'
        if (!endpoint.startsWith("http")) {
            endpoint = String.format("http://%s", endpoint);
        }
        switch (otlpTraceExporterSettings.getProtocol()) {
            case GRPC: {
                OtlpGrpcSpanExporterBuilder otlpGrpcSpanExporterBuilder = OtlpGrpcSpanExporter.builder()
                        .setEndpoint(endpoint)
                        .setTimeout(otlpTraceExporterSettings.getTimeout())
                        .setCompression(otlpTraceExporterSettings.getCompression().toString());
                if (otlpTraceExporterSettings.getHeaders() != null) {
                    for (Map.Entry<String, String> headerEntry : otlpTraceExporterSettings.getHeaders().entrySet()) {
                        otlpGrpcSpanExporterBuilder.addHeader(headerEntry.getKey(), headerEntry.getValue());
                    }
                }
                spanExporter = otlpGrpcSpanExporterBuilder.build();
                break;
            }
            case HTTP_PROTOBUF: {
                OtlpHttpSpanExporterBuilder otlpHttpSpanExporterBuilder = OtlpHttpSpanExporter.builder()
                        .setEndpoint(endpoint)
                        .setTimeout(otlpTraceExporterSettings.getTimeout())
                        .setCompression(otlpTraceExporterSettings.getCompression().toString());
                if (otlpTraceExporterSettings.getHeaders() != null) {
                    for (Map.Entry<String, String> headerEntry : otlpTraceExporterSettings.getHeaders().entrySet()) {
                        otlpHttpSpanExporterBuilder.addHeader(headerEntry.getKey(), headerEntry.getValue());
                    }
                }
                spanExporter = otlpHttpSpanExporterBuilder.build();
                break;
            }
        }
        log.info("Starting OTLP span exporter on {} '{}'", otlpTraceExporterSettings.getProtocol()
                .getConfigRepresentation(), endpoint);

        Attributes resourceAttributes = openTelemetryController.getResource().getAttributes();
        return new DelegatingSpanExporter(spanExporter, resourceAttributes);
    }
}
