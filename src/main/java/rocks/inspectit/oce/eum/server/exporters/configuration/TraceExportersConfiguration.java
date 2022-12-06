package rocks.inspectit.oce.eum.server.exporters.configuration;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.exporter.jaeger.thrift.JaegerThriftSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import rocks.inspectit.oce.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.oce.eum.server.configuration.model.exporters.ExporterEnabledState;
import rocks.inspectit.oce.eum.server.configuration.model.exporters.ExportersSettings;
import rocks.inspectit.oce.eum.server.configuration.model.exporters.trace.OtlpTraceExporterSettings;
import rocks.inspectit.oce.eum.server.configuration.model.exporters.trace.TraceExportersSettings;
import rocks.inspectit.oce.eum.server.configuration.model.exporters.trace.JaegerExporterSettings;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.Optional;

@Configuration
@Slf4j
public class TraceExportersConfiguration {

    @Autowired
    private EumServerConfiguration configuration;

    @PostConstruct
    public void logWrongJaegerConfig() {
        Optional.ofNullable(configuration.getExporters())
                .map(ExportersSettings::getTracing)
                .map(TraceExportersSettings::getJaeger)
                .filter((jaeger) -> !jaeger.getEnabled().isDisabled())
                .ifPresent(settings -> {
                    if (StringUtils.hasText(settings.getUrl()) && !StringUtils.hasText(settings.getEndpoint())) {
                        log.warn("In order to use Jaeger span exporter, please specify the grpc API 'endpoint' property instead of the 'url'.");
                    }
                });
    }

    @PostConstruct
    public void logEnabledButNoEndpoint() {
        Optional.ofNullable(configuration.getExporters())
                .map(ExportersSettings::getTracing)
                .map(TraceExportersSettings::getJaeger)
                .filter((jaeger) -> jaeger.getEnabled().equals(ExporterEnabledState.ENABLED))
                .ifPresent(settings -> {
                    if (!StringUtils.hasText(settings.getEndpoint())) {
                        log.warn("Jaeger Exporter is enabled but 'endpoint' is not set.");
                    }
                });
    }

    @PostConstruct
    void logDeprecatedServiceName() {
        Optional.ofNullable(configuration.getExporters())
                .map(ExportersSettings::getTracing)
                .map(TraceExportersSettings::getJaeger)
                .filter((jaeger) -> StringUtils.hasText(jaeger.getServiceName()))
                .ifPresent(settings -> {
                    if (StringUtils.hasText(settings.getServiceName())) {
                        log.warn("You are using the deprecated property 'inspectit-eum-server.exporter.tracing.jaeger.service-name'. Please use 'inspectit-eum-server.exporter.tracing.service-name' instead.");
                    }
                });
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty({"inspectit-eum-server.exporters.tracing.jaeger.enabled", "inspectit-eum-server.exporters.tracing.jaeger.endpoint"})
    @ConditionalOnExpression("(NOT new String('${inspectit-eum-server.exporters.tracing.jaeger.enabled}').toUpperCase().equals(T(rocks.inspectit.oce.eum.server.configuration.model.exporters.ExporterEnabledState).DISABLED.toString())) AND (new String('${inspectit-eum-server.exporters.tracing.jaeger.endpoint}').length() > 0)")
    public SpanExporter jaegerSpanExporter() {
        JaegerExporterSettings jaegerExporterSettings = configuration.getExporters().getTracing().getJaeger();

        String endpoint = jaegerExporterSettings.getEndpoint();
        // OTEL expects that the URI starts with 'http://' or 'https://'
        if (!endpoint.startsWith("http")) {
            endpoint = String.format("http://%s", endpoint);
        }
        log.info("Starting Jaeger span exporter on {} '{}'", jaegerExporterSettings.getProtocol(), endpoint);

        System.setProperty("otel.resource.attributes", "service.name=" + (StringUtils.hasText(jaegerExporterSettings.getServiceName()) ? jaegerExporterSettings.getServiceName() : configuration.getExporters()
                .getTracing()
                .getServiceName()));

        SpanExporter spanExporter = null;
        switch (jaegerExporterSettings.getProtocol()) {
            case GRPC: {
                spanExporter = JaegerGrpcSpanExporter.builder()
                        .setEndpoint(endpoint)
                        .setCompression(jaegerExporterSettings.getCompression().toString())
                        .setTimeout(jaegerExporterSettings.getTimeout())
                        .build();
                break;

            }
            case HTTP_THRIFT: {
                spanExporter = JaegerThriftSpanExporter.builder().setEndpoint(endpoint).build();
                break;
            }
        }
        return spanExporter;
    }

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
        System.setProperty("otel.resource.attributes", ResourceAttributes.SERVICE_NAME.getKey() + "=" + configuration.getExporters()
                .getTracing()
                .getServiceName());

        return spanExporter;
    }

}
