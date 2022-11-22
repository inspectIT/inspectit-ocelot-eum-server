package rocks.inspectit.oce.eum.server.exporters;

import com.google.protobuf.InvalidProtocolBufferException;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.grpc.protocol.AbstractUnaryGrpcService;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;
import io.opencensus.trace.TraceId;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceResponse;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.testcontainers.Testcontainers.exposeHostPorts;

/**
 * Base class for exporter integration tests. Verifies integration with the OpenTelemetry Collector. The Collector can be configured to accept the required data over gRPC or HTTP and exports the data over gRPC to a server running in process, allowing assertions to be made against the data.
 * This class is based on the {@link io.opentelemetry.integrationtest.OtlpExporterIntegrationTest}
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ExporterIntTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    TestRestTemplate restTemplate;

    public static final String SERVICE_NAME = "E2E-test";

    static final String COLLECTOR_TAG = "0.58.0";

    static final String COLLECTOR_IMAGE = "otel/opentelemetry-collector-contrib:" + COLLECTOR_TAG;

    static final Integer COLLECTOR_OTLP_GRPC_PORT = 4317;

    static final Integer COLLECTOR_OTLP_HTTP_PORT = 4318;

    static final Integer COLLECTOR_HEALTH_CHECK_PORT = 13133;

    static final Integer COLLECTOR_JAEGER_GRPC_PORT = 14250;

    static final Integer COLLECTOR_JAEGER_THRIFT_HTTP_PORT = 14268;

    static final Integer COLLECTOR_JAEGER_THRIFT_BINARY_PORT = 6832;

    static final Integer COLLECTOR_JAEGER_THRIFT_COMPACT_PORT = 6831;

    static final Integer COLLECTOR_PROMETHEUS_PORT = 8888;

    static final Integer COLLECTOR_INFLUX_DB1_PORT = 8086;

    static final int COLLECTOR_ZIPKIN_PORT = 9411;

    static final int COLLECTOR_TRACE_QUERY_PORT = 16686;

    static final String JAEGER_THRIFT_PATH = "/api/traces";

    static final String JAEGER_GRPC_PATH = "/v1/traces";

    private static final Logger LOGGER = Logger.getLogger(ExporterIntTestBase.class.getName());

    /**
     * The {@link OtlpGrpcServer} used as an exporter endpoint for the OpenTelemetry Collector
     */
    static OtlpGrpcServer grpcServer;

    /**
     * The OpenTelemetry Collector
     */
    static GenericContainer<?> collector;

    final static String DEFAULT_TRACE_ID = "497d4e959f574a77d0d3abf05523ec5c";

    @BeforeAll
    static void startCollector() {

        // start the gRPC server
        grpcServer = new OtlpGrpcServer();
        grpcServer.start();

        // Expose the port the in-process OTLP gRPC server will run on before the collector is
        // initialized so the collector can connect to it.
        exposeHostPorts(grpcServer.httpPort());

        collector = new GenericContainer<>(DockerImageName.parse(COLLECTOR_IMAGE)).withEnv("LOGGING_EXPORTER_LOG_LEVEL", "INFO")
                .withEnv("OTLP_EXPORTER_ENDPOINT", "host.testcontainers.internal:" + grpcServer.httpPort())
                .withEnv("PROMETHEUS_SCRAPE_TARGET", String.format("host.testcontainers.internal:%s", COLLECTOR_PROMETHEUS_PORT))
                .withClasspathResourceMapping("otel-config.yaml", "/otel-config.yaml", BindMode.READ_ONLY)
                .withCommand("--config", "/otel-config.yaml")
                .withLogConsumer(outputFrame -> LOGGER.log(Level.INFO, outputFrame.getUtf8String().replace("\n", "")))
                // expose all relevant ports
                .withExposedPorts(COLLECTOR_OTLP_GRPC_PORT, COLLECTOR_OTLP_HTTP_PORT, COLLECTOR_HEALTH_CHECK_PORT, COLLECTOR_JAEGER_THRIFT_HTTP_PORT, COLLECTOR_JAEGER_THRIFT_BINARY_PORT, COLLECTOR_JAEGER_THRIFT_COMPACT_PORT, COLLECTOR_JAEGER_GRPC_PORT, COLLECTOR_PROMETHEUS_PORT, COLLECTOR_INFLUX_DB1_PORT, COLLECTOR_ZIPKIN_PORT)
                .waitingFor(Wait.forHttp("/").forPort(COLLECTOR_HEALTH_CHECK_PORT));

        // collector.withStartupTimeout(Duration.of(1, ChronoUnit.MINUTES));
        // note: in case you receive the 'Caused by: org.testcontainers.containers.ContainerLaunchException: Timed out waiting for container port to open' exception,
        // uncomment the above line. The exception is probably caused by Docker Desktop hiccups and should only appear locally.
        collector.start();

        // build and register OpenTelemetrySdk
        //        SpanProcessor spanProcessor = BatchSpanProcessor.builder(SpanExporter.composite(OtlpGrpcSpanExporter.builder()
        //                        .setEndpoint(getEndpoint(COLLECTOR_OTLP_GRPC_PORT))
        //                        .build(), JaegerGrpcSpanExporter.builder()
        //                        .setEndpoint(getEndpoint(COLLECTOR_JAEGER_GRPC_PORT, JAEGER_GRPC_PATH))
        //                        .build(), OtlpHttpSpanExporter.builder().setEndpoint(getEndpoint(COLLECTOR_OTLP_HTTP_PORT)).build()))
        //                .build();
        //        openTelemetrySdk = OpenTelemetrySdk.builder()
        //                .setTracerProvider(SdkTracerProvider.builder().addSpanProcessor(spanProcessor).build())
        //                .setMeterProvider(SdkMeterProvider.builder().build())
        //                .buildAndRegisterGlobal();

    }

    @AfterAll
    static void stop() {
        grpcServer.stop().join();
        collector.stop();
    }

    @BeforeEach
    void reset() {
        grpcServer.reset();
    }

    /**
     * Gets the desired endpoint of the {@link #collector} constructed as 'http://{@link GenericContainer#getHost() collector.getHost()}:{@link GenericContainer#getMappedPort(int) collector.getMappedPort(port)}/path'
     *
     * @param originalPort the port to get the actual mapped port for
     * @param path         the path
     *
     * @return the constructed endpoint for the {@link #collector}
     */
    static String getEndpoint(Integer originalPort, String path) {
        return String.format("http://%s:%d/%s", collector.getHost(), collector.getMappedPort(originalPort), path.startsWith("/") ? path.substring(1) : path);
    }

    /**
     * Gets the desired endpoint of the {@link #collector} constructed as 'http://{@link GenericContainer#getHost() collector.getHost()}:{@link GenericContainer#getMappedPort(int) collector.getMappedPort(port)}'
     *
     * @param originalPort the port to get the actual mapped port for
     *
     * @return the constructed endpoint for the {@link #collector}
     */
    static String getEndpoint(Integer originalPort) {
        return String.format("http://%s:%d", collector.getHost(), collector.getMappedPort(originalPort));
    }

    /**
     * Waits for the spans to be exported to and received by the {@link #grpcServer}.
     *
     * @param expectedTraceId the expected trace id
     */
    void awaitSpansExported(String expectedTraceId) {

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {

            // get a flat list of spans
            Stream<List<io.opentelemetry.proto.trace.v1.Span>> spansLis = grpcServer.traceRequests.stream()
                    .flatMap(tr -> tr.getResourceSpansList()
                            .stream()
                            .flatMap(rs -> rs.getInstrumentationLibrarySpansList()
                                    .stream()
                                    .map(ils -> ils.getSpansList())));

            assertThat(spansLis.anyMatch(s -> s.stream()
                    .anyMatch(span -> TraceId.fromBytes(span.getTraceId().toByteArray())
                            .toLowerBase16()
                            .equals(expectedTraceId)))).isTrue();

        });

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

    /**
     * OpenTelemetry Protocol gRPC Server
     */
    static class OtlpGrpcServer extends ServerExtension {

        final List<ExportTraceServiceRequest> traceRequests = new ArrayList<>();

        final List<ExportMetricsServiceRequest> metricRequests = new ArrayList<>();

        final List<ExportLogsServiceRequest> logRequests = new ArrayList<>();

        private void reset() {
            traceRequests.clear();
            metricRequests.clear();
            logRequests.clear();
        }

        @Override
        protected void configure(ServerBuilder sb) {
            sb.service("/opentelemetry.proto.collector.trace.v1.TraceService/Export", new AbstractUnaryGrpcService() {
                @Override
                protected CompletionStage<byte[]> handleMessage(ServiceRequestContext ctx, byte[] message) {
                    try {
                        traceRequests.add(ExportTraceServiceRequest.parseFrom(message));
                    } catch (InvalidProtocolBufferException e) {
                        throw new UncheckedIOException(e);
                    }
                    return completedFuture(ExportTraceServiceResponse.getDefaultInstance().toByteArray());
                }
            });
            sb.service("/opentelemetry.proto.collector.metrics.v1.MetricsService/Export", new AbstractUnaryGrpcService() {
                @Override
                protected CompletionStage<byte[]> handleMessage(ServiceRequestContext ctx, byte[] message) {
                    try {
                        metricRequests.add(ExportMetricsServiceRequest.parseFrom(message));
                    } catch (InvalidProtocolBufferException e) {
                        throw new UncheckedIOException(e);
                    }
                    return completedFuture(ExportMetricsServiceResponse.getDefaultInstance().toByteArray());
                }
            });
            sb.service("/opentelemetry.proto.collector.logs.v1.LogsService/Export", new AbstractUnaryGrpcService() {
                @Override
                protected CompletionStage<byte[]> handleMessage(ServiceRequestContext ctx, byte[] message) {
                    try {
                        logRequests.add(ExportLogsServiceRequest.parseFrom(message));
                    } catch (InvalidProtocolBufferException e) {
                        throw new UncheckedIOException(e);
                    }
                    return completedFuture(ExportLogsServiceResponse.getDefaultInstance().toByteArray());
                }
            });
            sb.http(0);
        }
    }

}
