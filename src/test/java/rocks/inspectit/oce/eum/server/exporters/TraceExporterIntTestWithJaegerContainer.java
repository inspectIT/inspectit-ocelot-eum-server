package rocks.inspectit.oce.eum.server.exporters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import rocks.inspectit.oce.eum.server.configuration.model.exporters.ExporterEnabledState;
import rocks.inspectit.oce.eum.server.configuration.model.exporters.TransportProtocol;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
public class TraceExporterIntTestWithJaegerContainer {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final int QUERY_PORT = 16686;

    static final int COLLECTOR_JAEGER_GRPC_PORT = 14250;

    static final int COLLECTOR_JAEGER_HTTP_THRIFT_PORT = 14268;

    static final int COLLECTOR_OTLP_GRPC_PORT = 4317;

    static final int COLLECTOR_OTLP_HTTP_PORT = 4318;

    private static final int HEALTH_PORT = 14269;

    public static final String SERVICE_NAME = "E2E-test";

    final static String DEFAULT_TRACE_ID = "497d4e959f574a77d0d3abf05523ec5c";

    @Container
    public static GenericContainer<?> jaegerContainer = new GenericContainer<>("jaegertracing/all-in-one:latest").withExposedPorts(COLLECTOR_JAEGER_GRPC_PORT, COLLECTOR_JAEGER_HTTP_THRIFT_PORT, COLLECTOR_OTLP_GRPC_PORT, COLLECTOR_OTLP_HTTP_PORT, QUERY_PORT, HEALTH_PORT)
            .withEnv("COLLECTOR_OTLP_ENABLED", "true")
            .waitingFor(Wait.forHttp("/").forPort(HEALTH_PORT));

    /**
     * Gets the desired endpoint of the {@link #jaegerContainer} constructed as 'http://{@link GenericContainer#getHost() collector.getHost()}:{@link GenericContainer#getMappedPort(int) collector.getMappedPort(port)}/path'
     *
     * @param originalPort the port to get the actual mapped port for
     * @param path         the path
     *
     * @return the constructed endpoint for the {@link #jaegerContainer}
     */
    static String getEndpoint(Integer originalPort, String path) {
        return String.format("http://%s:%d/%s", jaegerContainer.getHost(), jaegerContainer.getMappedPort(originalPort), path.startsWith("/") ? path.substring(1) : path);
    }

    /**
     * Gets the desired endpoint of the {@link #jaegerContainer} constructed as 'http://{@link GenericContainer#getHost() collector.getHost()}:{@link GenericContainer#getMappedPort(int) collector.getMappedPort(port)}'
     *
     * @param originalPort the port to get the actual mapped port for
     *
     * @return the constructed endpoint for the {@link #jaegerContainer}
     */
    static String getEndpoint(Integer originalPort) {
        return String.format("http://%s:%d", jaegerContainer.getHost(), jaegerContainer.getMappedPort(originalPort));
    }

    /**
     * Posts a {@code Span} to {@link rocks.inspectit.oce.eum.server.rest.TraceController#spans(String)} using {@link #postSpan(String)} using the {@link #DEFAULT_TRACE_ID}
     */
    void postSpan() {
        postSpan(DEFAULT_TRACE_ID);
    }

    /**
     * Posts a {@code Span} to {@link rocks.inspectit.oce.eum.server.rest.TraceController#spans(String)}
     *
     * @param traceId the trace id to be used
     */
    void postSpan(String traceId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(getSpanString(traceId), headers);

        ResponseEntity<String> entity = restTemplate.postForEntity(String.format("http://localhost:%d/spans", port), request, String.class);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }

    /**
     * Returns a request string for a {@code Span}
     *
     * @param traceId the trace id
     *
     * @return
     */
    private String getSpanString(String traceId) {
        String now = System.currentTimeMillis() + "000001";
        return "{\"resourceSpans\":[{\"resource\":{\"attributes\":[{\"key\":\"service.name\",\"value\":{\"stringValue\":\"" + SERVICE_NAME + "\"}},{\"key\":\"telemetry.sdk.language\",\"value\":{\"stringValue\":\"webjs\"}},{\"key\":\"telemetry.sdk.name\",\"value\":{\"stringValue\":\"opentelemetry\"}},{\"key\":\"telemetry.sdk.version\",\"value\":{\"stringValue\":\"0.18.2\"}}],\"droppedAttributesCount\":0},\"instrumentationLibrarySpans\":[{\"spans\":[{\"traceId\":\"" + traceId + "\",\"spanId\":\"fc3d735ad8dd7399\",\"name\":\"HTTP GET\",\"kind\":3,\"startTimeUnixNano\":" + now + ",\"endTimeUnixNano\":" + now + ",\"attributes\":[{\"key\":\"http.method\",\"value\":{\"stringValue\":\"GET\"}},{\"key\":\"http.url\",\"value\":{\"stringValue\":\"http://localhost:1337?command=undefined\"}},{\"key\":\"http.response_content_length\",\"value\":{\"intValue\":665}},{\"key\":\"http.status_code\",\"value\":{\"intValue\":200}},{\"key\":\"http.status_text\",\"value\":{\"stringValue\":\"OK\"}},{\"key\":\"http.host\",\"value\":{\"stringValue\":\"localhost:1337\"}},{\"key\":\"http.scheme\",\"value\":{\"stringValue\":\"http\"}},{\"key\":\"http.user_agent\",\"value\":{\"stringValue\":\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.128 Safari/537.36\"}}],\"droppedAttributesCount\":0,\"events\":[{\"timeUnixNano\":1619187815416888600,\"name\":\"open\",\"attributes\":[],\"droppedAttributesCount\":0},{\"timeUnixNano\":1619187815417378600,\"name\":\"send\",\"attributes\":[],\"droppedAttributesCount\":0},{\"timeUnixNano\":1619187815418218800,\"name\":\"fetchStart\",\"attributes\":[],\"droppedAttributesCount\":0},{\"timeUnixNano\":1619187815420648700,\"name\":\"domainLookupStart\",\"attributes\":[],\"droppedAttributesCount\":0},{\"timeUnixNano\":1619187815420648700,\"name\":\"domainLookupEnd\",\"attributes\":[],\"droppedAttributesCount\":0},{\"timeUnixNano\":1619187815420648700,\"name\":\"connectStart\",\"attributes\":[],\"droppedAttributesCount\":0},{\"timeUnixNano\":1619170468572063700,\"name\":\"secureConnectionStart\",\"attributes\":[],\"droppedAttributesCount\":0},{\"timeUnixNano\":1619187815723468800,\"name\":\"connectEnd\",\"attributes\":[],\"droppedAttributesCount\":0},{\"timeUnixNano\":1619187815723523600,\"name\":\"requestStart\",\"attributes\":[],\"droppedAttributesCount\":0},{\"timeUnixNano\":1619187815732868600,\"name\":\"responseStart\",\"attributes\":[],\"droppedAttributesCount\":0},{\"timeUnixNano\":1619187815734768600,\"name\":\"responseEnd\",\"attributes\":[],\"droppedAttributesCount\":0},{\"timeUnixNano\":1619187815735928600,\"name\":\"loaded\",\"attributes\":[],\"droppedAttributesCount\":0}],\"droppedEventsCount\":0,\"status\":{\"code\":0},\"links\":[],\"droppedLinksCount\":0}],\"instrumentationLibrary\":{\"name\":\"@opentelemetry/instrumentation-xml-http-request\",\"version\":\"0.18.2\"}}]}]}";
    }

    boolean assertJaegerHasTrace() {
        return assertJaegerHasTrace(DEFAULT_TRACE_ID);
    }

    boolean assertJaegerHasTrace(String expectedTraceId) {
        try {
            String url = String.format("http://%s:%d/api/traces?service=%s", jaegerContainer.getHost(), jaegerContainer.getMappedPort(QUERY_PORT), SERVICE_NAME);

            ResponseEntity<String> result = restTemplate.getForEntity(url, String.class);

            System.out.println("Jaeger response: " + result.getStatusCodeValue());
            System.out.println("|- " + result.getBody());

            JsonNode json = objectMapper.readTree(result.getBody());
            return json.get("data").get(0).get("traceID").toString().equals("\"" + expectedTraceId + "\"");
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return false;
        }
    }

}

@DirtiesContext
@ContextConfiguration(initializers = OtlpHttpTraceExporterIntTestWithJaegerContainer.EnvInitializer.class)
class OtlpHttpTraceExporterIntTestWithJaegerContainer extends TraceExporterIntTestWithJaegerContainer {

    static class EnvInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            TestPropertyValues.of("inspectit-eum-server.exporters.tracing.otlp.enabled=" + ExporterEnabledState.ENABLED, "inspectit-eum-server.exporters.tracing.otlp.protocol=" + TransportProtocol.HTTP_PROTOBUF.getConfigRepresentation(), String.format("inspectit-eum-server.exporters.tracing.otlp.endpoint=%s/v1/traces", getEndpoint(COLLECTOR_OTLP_HTTP_PORT)))
                    .applyTo(applicationContext);
        }
    }

    @Test
    void otlpHttpTraceIntegration() {
        String httpTraceId = "497d4e959f574a77d0d3abf05523ec5b";
        postSpan(httpTraceId);
        Awaitility.waitAtMost(15, TimeUnit.SECONDS).until(() -> assertJaegerHasTrace(httpTraceId));
    }

}

@DirtiesContext
@ContextConfiguration(initializers = OtlpGrpcTraceExporterIntTestWithJaegerContainer.EnvInitializer.class)
class OtlpGrpcTraceExporterIntTestWithJaegerContainer extends TraceExporterIntTestWithJaegerContainer {

    static class EnvInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            TestPropertyValues.of("inspectit-eum-server.exporters.tracing.otlp.enabled=" + ExporterEnabledState.ENABLED, "inspectit-eum-server.exporters.tracing.otlp.endpoint=" + getEndpoint(COLLECTOR_OTLP_GRPC_PORT), "inspectit-eum-server.exporters.tracing.service-name=" + SERVICE_NAME, "inspectit-eum-server.exporters.tracing.otlp.protocol=" + TransportProtocol.GRPC.getConfigRepresentation())
                    .applyTo(applicationContext);
        }
    }

    @Test
    void otlGrpcTraceExporterIntegration() {
        String grpcTraceId = "497d4e959f574a77d0d3abf05523ec5a";
        postSpan(grpcTraceId);
        Awaitility.waitAtMost(15, TimeUnit.SECONDS).until(() -> assertJaegerHasTrace(grpcTraceId));
    }
}

@DirtiesContext
@ContextConfiguration(initializers = JaegerGrpcTraceExporterIntTestWithJaegerContainer.EnvInitializer.class)
class JaegerGrpcTraceExporterIntTestWithJaegerContainer extends TraceExporterIntTestWithJaegerContainer {

    static class EnvInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {

            TestPropertyValues.of("inspectit-eum-server.exporters.tracing.jaeger.enabled=" + ExporterEnabledState.ENABLED, "inspectit-eum-server.exporters.tracing.jaeger.endpoint=" + getEndpoint(COLLECTOR_JAEGER_GRPC_PORT), "inspectit-eum-server.exporters.tracing.service-name=" + JaegerExporterIntTest.SERVICE_NAME, "inspectit-eum-server.exporters.tracing.jaeger.protocol=" + TransportProtocol.GRPC.getConfigRepresentation())
                    .applyTo(applicationContext);
        }
    }

    @Test
    void jaegerTraceExporterIntegration() {
        String jaegerGrpcTraceId = "497d4e959f574a77d0d3abf05523ec5d";
        postSpan(jaegerGrpcTraceId);
        Awaitility.waitAtMost(15, TimeUnit.SECONDS).until(() -> assertJaegerHasTrace(jaegerGrpcTraceId));
    }
}

@DirtiesContext
@ContextConfiguration(initializers = JaegerHttpTraceExporterIntTestWithJaegerContainer.EnvInitializer.class)
class JaegerHttpTraceExporterIntTestWithJaegerContainer extends TraceExporterIntTestWithJaegerContainer {

    static class EnvInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            TestPropertyValues.of("inspectit-eum-server.exporters.tracing.jaeger.enabled=" + ExporterEnabledState.ENABLED, "inspectit-eum-server.exporters.tracing.jaeger.protocol=" + TransportProtocol.HTTP_THRIFT.getConfigRepresentation(), String.format("inspectit-eum-server.exporters.tracing.jaeger.endpoint=%s/api/traces", getEndpoint(COLLECTOR_JAEGER_HTTP_THRIFT_PORT)))
                    .applyTo(applicationContext);
        }
    }

    @Test
    void jaegerHttpTraceIntegration() {
        String httpTraceId = "497d4e959f574a77d0d3abf05523ec5e";
        postSpan(httpTraceId);
        Awaitility.waitAtMost(15, TimeUnit.SECONDS).until(() -> assertJaegerHasTrace(httpTraceId));
    }

}