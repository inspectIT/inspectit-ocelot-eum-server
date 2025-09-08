package rocks.inspectit.ocelot.eum.server.exporters.metrics;

import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporterBuilder;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporterBuilder;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.export.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import rocks.inspectit.ocelot.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.ocelot.eum.server.configuration.model.exporters.TransportProtocol;
import rocks.inspectit.ocelot.eum.server.configuration.model.exporters.metrics.OtlpMetricsExporterSettings;

import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

@Component
@Slf4j
public class OtlpMetricsExporterService {

    private final List<TransportProtocol> SUPPORTED_PROTOCOLS = Arrays.asList(TransportProtocol.GRPC, TransportProtocol.HTTP_PROTOBUF);

    @Autowired
    private EumServerConfiguration configuration;

    @Autowired
    private ScheduledExecutorService executor;

    private MetricExporter metricExporter;

    private ScheduledFuture<?> exporterTask;

    OtlpMetricsExporterSettings otlpMetricsExporterSettings;

    @Getter
    private boolean enabled;

    // TODO I'm not happy with this style
    @Bean
    @ConditionalOnProperty({"inspectit-eum-server.exporters.metrics.otlp.enabled", "inspectit-eum-server.exporters.metrics.otlp.endpoint"})
    @ConditionalOnExpression("(NOT new String('${inspectit-eum-server.exporters.metrics.otlp.enabled}').toUpperCase().equals(T(rocks.inspectit.ocelot.eum.server.configuration.model.exporters.ExporterEnabledState).DISABLED.toString())) AND (new String('${inspectit-eum-server.exporters.metrics.otlp.endpoint}').length() > 0)")
    MetricReader otlpService() {
        checkProtocol();
        enabled = true;
        otlpMetricsExporterSettings = configuration.getExporters().getMetrics().getOtlp();
        AggregationTemporalitySelector aggregationTemporalitySelector = otlpMetricsExporterSettings.getPreferredTemporality() == AggregationTemporality.DELTA ? AggregationTemporalitySelector.deltaPreferred() : AggregationTemporalitySelector.alwaysCumulative();

        String endpoint = configuration.getExporters().getMetrics().getOtlp().getEndpoint();
        // OTEL expects that the URI starts with 'http://' or 'https://'
        if (!endpoint.startsWith("http")) {
            endpoint = String.format("http://%s", endpoint);
        }
        try {
            switch (otlpMetricsExporterSettings.getProtocol()) {
                case GRPC: {
                    OtlpGrpcMetricExporterBuilder metricExporterBuilder = OtlpGrpcMetricExporter.builder()
                            .setAggregationTemporalitySelector(aggregationTemporalitySelector)
                            .setEndpoint(endpoint)
                            .setCompression(otlpMetricsExporterSettings.getCompression().toString())
                            .setTimeout(otlpMetricsExporterSettings.getTimeout());
                    if (otlpMetricsExporterSettings.getHeaders() != null) {
                        for (Map.Entry<String, String> headerEntry : otlpMetricsExporterSettings.getHeaders()
                                .entrySet()) {
                            metricExporterBuilder.addHeader(headerEntry.getKey(), headerEntry.getValue());
                        }
                    }
                    metricExporter = metricExporterBuilder.build();
                    break;
                }
                case HTTP_PROTOBUF: {
                    OtlpHttpMetricExporterBuilder metricExporterBuilder = OtlpHttpMetricExporter.builder()
                            .setAggregationTemporalitySelector(aggregationTemporalitySelector)
                            .setEndpoint(endpoint)
                            .setCompression(otlpMetricsExporterSettings.getCompression().toString())
                            .setTimeout(otlpMetricsExporterSettings.getTimeout());
                    if (otlpMetricsExporterSettings.getHeaders() != null) {
                        for (Map.Entry<String, String> headerEntry : otlpMetricsExporterSettings.getHeaders()
                                .entrySet()) {
                            metricExporterBuilder.addHeader(headerEntry.getKey(), headerEntry.getValue());
                        }
                    }
                    metricExporter = metricExporterBuilder.build();
                    break;
                }
            }

            log.info("Starting OTLP metric exporter with {} on '{}'", otlpMetricsExporterSettings.getProtocol(), otlpMetricsExporterSettings.getEndpoint());
            return PeriodicMetricReader
                    .builder(metricExporter)
                    .setInterval(otlpMetricsExporterSettings.getExportInterval())
                    .build();

        } catch (Exception e) {
            log.error("Error starting OTLP metric exporter", e);
            throw e;
        }
    }

    private void checkProtocol() {
        @Valid OtlpMetricsExporterSettings otlp = configuration.getExporters().getMetrics().getOtlp();
        if (!otlp.getEnabled().isDisabled()) {
            if (!SUPPORTED_PROTOCOLS.contains(otlp.getProtocol())) {
                throw new IllegalArgumentException("OTLP Metric Exporter is enabled, but wrong 'protocol' is specified. " +
                        "Supported values are " + Arrays.toString(SUPPORTED_PROTOCOLS.stream().map(TransportProtocol::getConfigRepresentation).toArray()));
            }
        }
    }

    @PreDestroy
    private void doDisable() {
        enabled = false;
        if (exporterTask != null) {
            log.info("Stopping OTLP metric exporter");
            exporterTask.cancel(false);
        }
        if (metricExporter != null) {
            metricExporter.flush();
            metricExporter.close();
        }
    }
}
