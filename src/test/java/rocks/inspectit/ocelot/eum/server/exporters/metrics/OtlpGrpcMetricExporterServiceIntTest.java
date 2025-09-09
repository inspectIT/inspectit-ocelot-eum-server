package rocks.inspectit.ocelot.eum.server.exporters.metrics;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import rocks.inspectit.ocelot.eum.server.configuration.model.exporters.ExporterEnabledState;
import rocks.inspectit.ocelot.eum.server.configuration.model.exporters.TransportProtocol;
import rocks.inspectit.ocelot.eum.server.exporters.ExporterIntTestBaseWithOtelCollector;

import java.util.Map;

@DirtiesContext
@ContextConfiguration(initializers = OtlpGrpcMetricExporterServiceIntTest.EnvInitializer.class)
public class OtlpGrpcMetricExporterServiceIntTest extends ExporterIntTestBaseWithOtelCollector {
    static class EnvInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            TestPropertyValues.of("inspectit-eum-server.exporters.metrics.otlp.enabled=" + ExporterEnabledState.ENABLED, "inspectit-eum-server.exporters.metrics.otlp.endpoint=" + getEndpoint(COLLECTOR_OTLP_GRPC_PORT), "inspectit-eum-server.exporters.metrics.otlp.export-interval=1s", "inspectit-eum-server.exporters.metrics.otlp.protocol=" + TransportProtocol.GRPC.getConfigRepresentation(), "inspectit-eum-server.exporters.metrics.service-name=" + SERVICE_NAME)
                    .applyTo(applicationContext);
            TestPropertyValues.of("inspectit-eum-server.self-monitoring.enabled=" + false).applyTo(applicationContext);
        }
    }

    @Test
    void verifyOtlpGrpcMetrics() throws Exception { // TODO fix
        Map<String, String> beacon = getBasicBeacon();
        // fake beacon that we don't expect
        beacon.put(FAKE_BEACON_KEY_NAME, "1338");
        // real beacon that we expect
        // use a different metric (t_page) as the different metric exporter test cases overload the metrics
        beacon.put(BEACON_PAGE_READY_TIME_KEY_NAME, "42");
        sendBeacon(beacon);
        // wait until metrics have been exported
        awaitMetricsExported(METRIC_PAGE_READY_TIME_KEY_NAME, 42);
        assertMetric(1338, false);
    }

}
