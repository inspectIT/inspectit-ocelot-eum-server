package rocks.inspectit.ocelot.eum.server.exporters.metrics;

import io.opentelemetry.api.GlobalOpenTelemetry;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.TestSocketUtils;
import rocks.inspectit.ocelot.eum.server.exporters.ExporterIntMockMvcTestBase;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test of PrometheusExporterService
 */
@SpringBootTest
@ContextConfiguration(initializers = PrometheusExporterServiceIntTest.EnvInitializer.class)
@DirtiesContext
class PrometheusExporterServiceIntTest extends ExporterIntMockMvcTestBase {

    private static int PROMETHEUS_PORT;

    static class EnvInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            PROMETHEUS_PORT = TestSocketUtils.findAvailableTcpPort();
            TestPropertyValues.of(String.format("inspectit-eum-server.exporters.metrics.prometheus.port=%d", PROMETHEUS_PORT))
                    .applyTo(applicationContext);
            TestPropertyValues.of(String.format("inspectit-eum-server.exporters.metrics.prometheus.enabled=ENABLED"))
                    .applyTo(applicationContext);
            TestPropertyValues.of("inspectit-eum-server.self-monitoring.enabled=" + false).applyTo(applicationContext);
        }
    }

    static CloseableHttpClient httpClient;

    @BeforeEach
    void initClient() {
        GlobalOpenTelemetry.resetForTest();
        HttpClientBuilder builder = HttpClientBuilder.create();
        httpClient = builder.build();
    }

    @AfterEach
    void closeClient() throws Exception {
        httpClient.close();
    }

    @Test
    void testDefaultSettings() throws Exception {
        HttpGet httpGet = new HttpGet("http://localhost:" + PROMETHEUS_PORT + "/metrics");
        int statusCode = httpClient.execute(httpGet).getCode();

        assertThat(statusCode).isEqualTo(200);
    }

    /**
     * The application should expose no view, since no beacon entry maps to the default implementation.
     */
    @Test
    void expectNoViews() throws Exception {
        Map<String, String> beacon = getBasicBeacon();
        beacon.put(FAKE_BEACON_KEY_NAME, "Fake Value");

        sendBeacon(beacon);

        await().atMost(15, TimeUnit.SECONDS).pollInterval(2, TimeUnit.SECONDS).untilAsserted(() -> {
            ClassicHttpResponse response = httpClient.execute(new HttpGet("http://localhost:" + PROMETHEUS_PORT + "/metrics)"));
            HttpClientResponseHandler<String> responseHandler = new BasicHttpClientResponseHandler();
            assertThat(response.getCode()).isEqualTo(200);
            assertThat(responseHandler.handleResponse(response)).doesNotContain("Fake Value");
        });
    }

    /**
     * The application should expose one view, since one beacon entry maps to the default implementation.
     */
    @Test
    void expectOneView() throws Exception {
        Map<String, String> beacon = getBasicBeacon();
        // send the beacon. use a different metric (t_load) as the different metric exporter test cases overload the metrics
        beacon.put(BEACON_LOAD_TIME_KEY_NAME, "12");
        sendBeacon(beacon);

        // replace '/' with '_' for Prometheus metric name
        String metricKeyName = METRIC_LOAD_TIME_KEY_NAME.replaceAll("/","_");

        await().atMost(15, TimeUnit.SECONDS).pollInterval(2, TimeUnit.SECONDS).untilAsserted(() -> {
            ClassicHttpResponse response = httpClient.execute(new HttpGet("http://localhost:" + PROMETHEUS_PORT + "/metrics)"));
            HttpClientResponseHandler<String> responseHandler = new BasicHttpClientResponseHandler();
            String responseString = responseHandler.handleResponse(response);
            assertThat(responseString).contains(metricKeyName+"_milliseconds_sum{COUNTRY_CODE=\"\",OS=\"\",URL=\"http://test.com/login\",otel_scope_name=\"rocks.inspectit.ocelot\"} 12.0");
        });
    }
}
