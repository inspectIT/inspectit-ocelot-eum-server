package rocks.inspectit.oce.eum.server.exporters;

import io.opencensus.metrics.Metrics;
import io.opencensus.metrics.export.Metric;
import io.opencensus.metrics.export.MetricProducer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporterBuilder;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporterBuilder;
import io.opentelemetry.opencensusshim.internal.metrics.MetricAdapter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReaderBuilder;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import rocks.inspectit.oce.eum.server.AppStartupRunner;
import rocks.inspectit.oce.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.oce.eum.server.configuration.model.exporters.ExporterEnabledState;
import rocks.inspectit.oce.eum.server.configuration.model.exporters.TransportProtocol;
import rocks.inspectit.oce.eum.server.configuration.model.exporters.metrics.OtlpMetricsExporterSettings;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
@Slf4j
public class OtlpMetricsExporterService {

    private final List<TransportProtocol> SUPPORTED_PROTOCOLS = Arrays.asList(TransportProtocol.GRPC, TransportProtocol.HTTP_PROTOBUF);

    @Autowired
    private EumServerConfiguration configuration;

    @Autowired
    private ScheduledExecutorService executor;

    @Autowired
    private AppStartupRunner appStartupRunner;

    private Supplier<Set<MetricProducer>> metricProducerSupplier;

    MetricExporter metricExporter;

    PeriodicMetricReaderBuilder metricReaderBuilder;

    OpenTelemetrySdk openTelemetry;

    SdkMeterProvider meterProvider;

    private ScheduledFuture<?> exporterTask;

    private Resource otelResource;

    OtlpMetricsExporterSettings otlpMetricsExporterSettings;

    @Getter
    private boolean enabled;

    private boolean shouldEnable() {
        @Valid OtlpMetricsExporterSettings otlp = configuration.getExporters().getMetrics().getOtlp();
        if (!otlp.getEnabled().isDisabled()) {
            if (SUPPORTED_PROTOCOLS.contains(otlp.getProtocol())) {
                if (StringUtils.hasText(otlp.getEndpoint())) {
                    return true;
                } else if (StringUtils.hasText(otlp.getEndpoint())) {
                    log.warn("OTLP Metric Exporter is enabled but 'endpoint' is not set.");
                    return true;
                }
            }
            if (otlp.getEnabled().equals(ExporterEnabledState.ENABLED)) {
                if (!SUPPORTED_PROTOCOLS.contains(otlp.getProtocol())) {
                    log.warn("OTLP Metric Exporter is enabled, but wrong 'protocol' is specified. Supported values are {}", Arrays.toString(SUPPORTED_PROTOCOLS.stream()
                            .map(TransportProtocol::getConfigRepresentation)
                            .toArray()));
                }
                if (!StringUtils.hasText(otlp.getEndpoint())) {
                    log.warn("OTLP Metric Exporter is enabled but 'endpoint' is not set.");
                }
            }
        }

        return false;
    }

    @PostConstruct
    void doEnable() {

        if (shouldEnable()) {
            enabled = true;
            otlpMetricsExporterSettings = configuration.getExporters().getMetrics().getOtlp();
            AggregationTemporalitySelector aggregationTemporalitySelector = otlpMetricsExporterSettings.getPreferredTemporality() == AggregationTemporality.DELTA ? AggregationTemporalitySelector.deltaPreferred() : AggregationTemporalitySelector.alwaysCumulative();
            otelResource = Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, configuration.getExporters()
                    .getMetrics()
                    .getServiceName(), AttributeKey.stringKey("inspectit.eum-server.version"), appStartupRunner.getServerVersion(), ResourceAttributes.TELEMETRY_SDK_VERSION, appStartupRunner.getOpenTelemetryVersion(), ResourceAttributes.TELEMETRY_SDK_LANGUAGE, "java", ResourceAttributes.TELEMETRY_SDK_NAME, "opentelemetry"));
            String endpoint = configuration.getExporters().getMetrics().getOtlp().getEndpoint();
            // OTEL expects that the URI starts with 'http://' or 'https://'
            if (!endpoint.startsWith("http")) {
                endpoint = String.format("http://%s", endpoint);
            }
            try {
                metricProducerSupplier = () -> Metrics.getExportComponent()
                        .getMetricProducerManager()
                        .getAllMetricProducer();

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

                exporterTask = executor.scheduleAtFixedRate(this::export, otlpMetricsExporterSettings.getExportInterval()
                        .toMillis(), otlpMetricsExporterSettings.getExportInterval().toMillis(), TimeUnit.MILLISECONDS);

                log.info("Starting OTLP metric exporter with {} on '{}'", otlpMetricsExporterSettings.getProtocol(), otlpMetricsExporterSettings.getEndpoint());
            } catch (Exception e) {
                log.error("Error starting OTLP metric exporter", e);
                throw e;
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

    private void export() {
        List<Metric> metrics = metricProducerSupplier.get()
                .stream()
                .flatMap(metricProducer -> metricProducer.getMetrics().stream())
                .collect(Collectors.toList());

        List<MetricData> convertedMetrics = metrics.stream()
                .map(metric -> MetricAdapter.convert(otelResource, metric))
                .collect(Collectors.toList());

        metricExporter.export(convertedMetrics);
    }
}
