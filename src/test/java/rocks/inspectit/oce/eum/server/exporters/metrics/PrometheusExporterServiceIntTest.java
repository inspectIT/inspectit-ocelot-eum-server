package rocks.inspectit.oce.eum.server.exporters.metrics;

import io.prometheus.client.CollectorRegistry;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.util.SocketUtils;
import rocks.inspectit.oce.eum.server.exporters.ExporterIntMockMvcTestBase;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Integration test of PrometheusExporterService
 */
@SpringBootTest
@ContextConfiguration(initializers = PrometheusExporterServiceIntTest.EnvInitializer.class)
@DirtiesContext
public class PrometheusExporterServiceIntTest extends ExporterIntMockMvcTestBase {

    private static int PROMETHEUS_PORT;

    static class EnvInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            PROMETHEUS_PORT = SocketUtils.findAvailableTcpPort(20000);
            TestPropertyValues.of(String.format("inspectit-eum-server.exporters.metrics.prometheus.port=%d", PROMETHEUS_PORT))
                    .applyTo(applicationContext);
            TestPropertyValues.of(String.format("inspectit-eum-server.exporters.metrics.prometheus.enabled=ENABLED"))
                    .applyTo(applicationContext);
            TestPropertyValues.of("inspectit-eum-server.self-monitoring.enabled=" + false).applyTo(applicationContext);
        }
    }

    private static CloseableHttpClient httpClient;
    @BeforeAll
    public static void beforeClass() {
        CollectorRegistry.defaultRegistry.clear();
    }

    @BeforeEach
    public void initClient() {
        HttpClientBuilder builder = HttpClientBuilder.create();
        httpClient = builder.build();
    }

    @AfterEach
    public void closeClient() throws Exception {
        httpClient.close();
    }

    @Test
    public void testDefaultSettings() throws Exception {
        HttpGet httpGet = new HttpGet("http://localhost:" + PROMETHEUS_PORT + "/metrics");
        int statusCode = httpClient.execute(httpGet).getStatusLine().getStatusCode();

        assertThat(statusCode).isEqualTo(200);
    }

    /**
     * The application should expose no view, since no beacon entry maps to the default implementation.
     *
     * @throws Exception
     */
    @Test
    public void expectNoViews() throws Exception {
        Map<String, String> beacon = getBasicBeacon();
        beacon.put(FAKE_BEACON_KEY_NAME, "Fake Value");

        sendBeacon(beacon);

        await().atMost(15, TimeUnit.SECONDS).pollInterval(2, TimeUnit.SECONDS).untilAsserted(() -> {
            HttpResponse response = httpClient.execute(new HttpGet("http://localhost:" + PROMETHEUS_PORT + "/metrics)"));
            ResponseHandler<String> responseHandler = new BasicResponseHandler();
            assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
            assertThat(responseHandler.handleResponse(response)).doesNotContain("Fake Value");
        });
    }

    /**
     * The application should expose one view, since one beacon entry maps to the default implementation.
     *
     * @throws Exception
     */
    @Test
    public void expectOneView() throws Exception {
        Map<String, String> beacon = getBasicBeacon();
        // send the beacon. use a different metric (t_load) as the different metric exporter test cases overload the metrics
        beacon.put(BEACON_LOAD_TIME_KEY_NAME, "12");
        sendBeacon(beacon);

        // replace '/' with '_' for Prometheus metric name
        String metricKeyName = METRIC_LOAD_TIME_KEY_NAME.replaceAll("/","_");

        await().atMost(15, TimeUnit.SECONDS).pollInterval(2, TimeUnit.SECONDS).untilAsserted(() -> {
            HttpResponse response = httpClient.execute(new HttpGet("http://localhost:" + PROMETHEUS_PORT + "/metrics)"));
            ResponseHandler<String> responseHandler = new BasicResponseHandler();
            assertThat(responseHandler.handleResponse(response)).contains(metricKeyName+"{COUNTRY_CODE=\"\",OS=\"\",URL=\"http://test.com/login\",} 12.0");
        });
    }
}
