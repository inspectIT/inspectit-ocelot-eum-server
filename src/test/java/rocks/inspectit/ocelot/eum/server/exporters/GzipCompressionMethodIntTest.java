package rocks.inspectit.ocelot.eum.server.exporters;

import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import rocks.inspectit.ocelot.eum.server.configuration.model.CompressionMethod;
import rocks.inspectit.ocelot.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.ocelot.eum.server.configuration.model.exporters.ExporterEnabledState;
import rocks.inspectit.ocelot.eum.server.configuration.model.exporters.TransportProtocol;
import rocks.inspectit.ocelot.eum.server.exporters.metrics.OtlpMetricsExporterService;
import rocks.inspectit.ocelot.eum.server.exporters.tracing.DelegatingSpanExporter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the {@link CompressionMethod#GZIP} for the {@link OtlpMetricsExporterService} and {@link OtlpGrpcSpanExporter}.
 */
@DirtiesContext
@ContextConfiguration(initializers = GzipCompressionMethodIntTest.EnvInitializer.class)
class GzipCompressionMethodIntTest extends ExporterIntTestBaseWithOtelCollector {

    @Autowired
    EumServerConfiguration configuration;

    @Autowired
    List<SpanExporter> spanExporters;

    @Autowired
    OtlpMetricsExporterService otlpMetricsExporterService;

    static class EnvInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            TestPropertyValues.of("inspectit-eum-server.exporters.tracing.otlp.enabled=" + ExporterEnabledState.ENABLED, "inspectit-eum-server.exporters.tracing.otlp.endpoint=" + getEndpoint(COLLECTOR_OTLP_GRPC_PORT), "inspectit-eum-server.exporters.tracing.service-name=" + SERVICE_NAME, "inspectit-eum-server.exporters.tracing.otlp.protocol=" + TransportProtocol.GRPC.getConfigRepresentation())
                    .applyTo(applicationContext);
            TestPropertyValues.of("inspectit-eum-server.exporters.tracing.otlp.compression=" + CompressionMethod.GZIP)
                    .applyTo(applicationContext);

            TestPropertyValues.of("inspectit-eum-server.exporters.metrics.otlp.enabled=" + ExporterEnabledState.ENABLED, "inspectit-eum-server.exporters.metrics.otlp.endpoint=" + getEndpoint(COLLECTOR_OTLP_GRPC_PORT), "inspectit-eum-server.exporters.metrics.otlp.export-interval=1s", "inspectit-eum-server.exporters.metrics.otlp.protocol=" + TransportProtocol.GRPC.getConfigRepresentation(), "inspectit-eum-server.exporters.metrics.service-name=" + SERVICE_NAME)
                    .applyTo(applicationContext);
            TestPropertyValues.of("inspectit-eum-server.self-monitoring.enabled=" + false).applyTo(applicationContext);
            TestPropertyValues.of("inspectit-eum-server.exporters.metrics.otlp.compression=" + CompressionMethod.GZIP)
                    .applyTo(applicationContext);
        }
    }

    @Test
    void testCompressionMethodSpanExporter() {
        assertThat(configuration.getExporters()
                .getTracing()
                .getOtlp()
                .getCompression()).isEqualTo(CompressionMethod.GZIP);

        Optional<SpanExporter> spanExporter = spanExporters.stream()
                .filter(sE -> sE instanceof DelegatingSpanExporter)
                .findFirst();
        assertThat(spanExporter.isPresent()).isTrue();
    }

    @Test
    void verifyTraceSentGrpc() throws Exception {
        String grpcTraceId = "497d4e959f574a77d0d3abf05523ec5f";
        postSpan(grpcTraceId);
        awaitSpansExported(grpcTraceId);
    }

    @Test
    void testCompressionMethodMetricExporter() {
        assertThat(configuration.getExporters()
                .getMetrics()
                .getOtlp()
                .getCompression()).isEqualTo(CompressionMethod.GZIP);

        assertThat(otlpMetricsExporterService.isEnabled()).isTrue();
    }

    @Test
    void verifyOtlpGrpcMetrics() throws Exception {
        Map<String, String> beacon = getBasicBeacon();
        // fake beacon that we don't expect
        beacon.put(FAKE_BEACON_KEY_NAME, "1334");
        // real beacon that we expect
        // use a different metric (rt.end) as the different metric exporter test cases overload the metrics
        beacon.put(BEACON_END_TIMESTAMP_KEY_NAME, "41");
        sendBeacon(beacon);
        // wait until metrics have been exported
        awaitMetricsExported(METRIC_END_TIMESTAMP_KEY_NAME);
        assertGauge(METRIC_END_TIMESTAMP_KEY_NAME, 41);
    }
}
