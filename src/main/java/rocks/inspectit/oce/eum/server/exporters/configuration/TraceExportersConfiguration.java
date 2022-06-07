package rocks.inspectit.oce.eum.server.exporters.configuration;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
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
import rocks.inspectit.oce.eum.server.configuration.model.exporters.trace.TraceExportersSettings;
import rocks.inspectit.oce.eum.server.configuration.model.exporters.trace.JaegerExporterSettings;

import javax.annotation.PostConstruct;
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

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty({"inspectit-eum-server.exporters.tracing.jaeger.enabled", "inspectit-eum-server.exporters.tracing.jaeger.endpoint"})
    @ConditionalOnExpression("(NOT new String('${inspectit-eum-server.exporters.tracing.jaeger.enabled}').toUpperCase().equals(T(rocks.inspectit.oce.eum.server.configuration.model.exporters.ExporterEnabledState).DISABLED.toString())) AND (new String('${inspectit-eum-server.exporters.tracing.jaeger.endpoint}').length() > 0)")
    public SpanExporter jaegerSpanExporter() {
        JaegerExporterSettings jaegerExporterSettings = configuration.getExporters().getTracing().getJaeger();

        ManagedChannel channel = ManagedChannelBuilder.forTarget(jaegerExporterSettings.getEndpoint())
                .usePlaintext()
                .build();

        log.info("Starting Jaeger span exporter on grpc '{}'", jaegerExporterSettings.getEndpoint());
        System.setProperty("otel.resource.attributes", "service.name=" + jaegerExporterSettings.getServiceName());

        return JaegerGrpcSpanExporter.builder().setChannel(channel).build();
    }

}
