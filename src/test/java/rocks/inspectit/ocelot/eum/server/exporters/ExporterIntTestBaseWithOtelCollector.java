package rocks.inspectit.ocelot.eum.server.exporters;

import com.google.protobuf.InvalidProtocolBufferException;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.grpc.protocol.AbstractUnaryGrpcService;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceResponse;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
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
 * Base class for exporter integration tests. Verifies integration with the OpenTelemetry Collector.
 * The Collector can be configured to accept the required data over gRPC or HTTP and exports the data over gRPC
 * to a server running in process, allowing assertions to be made against the data.
 * This class is based on the {@link io.opentelemetry.integrationtest.OtlpExporterIntegrationTest}.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
public class ExporterIntTestBaseWithOtelCollector extends ExporterIntMockMvcTestBase {

    protected static final String OTLP_HTTP_METRICS_PATH = "/v1/metrics";

    static final String COLLECTOR_TAG = "0.100.0";

    static final String COLLECTOR_IMAGE = "otel/opentelemetry-collector-contrib:" + COLLECTOR_TAG;

    protected static final Integer COLLECTOR_OTLP_GRPC_PORT = 4317;

    protected static final Integer COLLECTOR_OTLP_HTTP_PORT = 4318;

    static final Integer COLLECTOR_HEALTH_CHECK_PORT = 13133;

    static final Integer COLLECTOR_PROMETHEUS_PORT = 8888;

    static final Integer COLLECTOR_PROMETHEUS_RECEIVER_PORT = 8889;

    static final int COLLECTOR_ZIPKIN_PORT = 9411;

    private static final Logger LOGGER = Logger.getLogger(ExporterIntTestBaseWithOtelCollector.class.getName());

    /**
     * The {@link OtlpGrpcServer} used as an exporter endpoint for the OpenTelemetry Collector
     */
    static OtlpGrpcServer grpcServer;

    /**
     * The OpenTelemetry Collector
     */
    static GenericContainer<?> collector;

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
                .withEnv("PROMETHEUS_INTEGRATION_TEST_SCRAPE_TARGET", String.format("host.testcontainers.internal:%s", COLLECTOR_PROMETHEUS_RECEIVER_PORT))
                .withClasspathResourceMapping("otel-config.yaml", "/otel-config.yaml", BindMode.READ_ONLY)
                .withCommand("--config", "/otel-config.yaml")
                .withLogConsumer(outputFrame -> LOGGER.log(Level.INFO, outputFrame.getUtf8String().replace("\n", "")))
                // expose all relevant ports
                .withExposedPorts(COLLECTOR_OTLP_GRPC_PORT, COLLECTOR_OTLP_HTTP_PORT, COLLECTOR_HEALTH_CHECK_PORT, COLLECTOR_PROMETHEUS_PORT, COLLECTOR_ZIPKIN_PORT, COLLECTOR_PROMETHEUS_RECEIVER_PORT)
                .waitingFor(Wait.forHttp("/").forPort(COLLECTOR_HEALTH_CHECK_PORT));

        //collector.withStartupTimeout(Duration.of(1, ChronoUnit.MINUTES));
        // note: in case you receive the 'Caused by: org.testcontainers.containers.ContainerLaunchException: Timed out waiting for container port to open' exception,
        // uncomment the above line. The exception is probably caused by Docker Desktop hiccups and should only appear locally.
        collector.start();
    }

    @AfterAll
    static void stop() {
        grpcServer.stop().join();
        collector.stop();
    }

    @BeforeEach
    void reset() {
        GlobalOpenTelemetry.resetForTest();
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
    protected static String getEndpoint(Integer originalPort, String path) {
        return String.format("http://%s:%d/%s", collector.getHost(), collector.getMappedPort(originalPort), path.startsWith("/") ? path.substring(1) : path);
    }

    /**
     * Gets the desired endpoint of the {@link #collector} constructed as 'http://{@link GenericContainer#getHost() collector.getHost()}:{@link GenericContainer#getMappedPort(int) collector.getMappedPort(port)}'
     *
     * @param originalPort the port to get the actual mapped port for
     *
     * @return the constructed endpoint for the {@link #collector}
     */
    protected static String getEndpoint(Integer originalPort) {
        return String.format("http://%s:%d", collector.getHost(), collector.getMappedPort(originalPort));
    }

    /**
     * Waits for the spans to be exported to and received by the {@link #grpcServer}.
     *
     * @param expectedTraceId the expected trace id
     */
    protected void awaitSpansExported(String expectedTraceId) {

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {

            // get a flat list of spans
            Stream<List<io.opentelemetry.proto.trace.v1.Span>> spansLis = grpcServer.traceRequests.stream()
                    .flatMap(tr -> tr.getResourceSpansList()
                            .stream()
                            .flatMap(rs -> rs.getScopeSpansList()
                                    .stream()
                                    .map(ScopeSpans::getSpansList)));

            assertThat(spansLis.anyMatch(s -> s.stream()
                    .anyMatch(span -> TraceId.fromBytes(span.getTraceId().toByteArray())
                            .equals(expectedTraceId)))).isTrue();
        });
    }

    /**
     * Verifies that the metric has been exported to and received by the {@link #grpcServer}
     *
     * @param metricName
     */
    protected void awaitMetricsExported(String metricName) {
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertThat(grpcServer.metricRequests.stream())
                        .anyMatch(mReq -> mReq.getResourceMetricsList()
                            .stream()
                            .anyMatch(rm ->
                                // check for the given metrics
                                rm.getScopeMetrics(0)
                                        .getMetricsList()
                                        .stream()
                                        .anyMatch(metric -> metric.getName().equalsIgnoreCase(metricName))
                            )
                        )
                );
    }

    /**
     * Checks if a histogram with the given count and sum has been recorded
     *
     * @param count the expected data point count
     * @param sum the expected data point sum
     */
    protected void assertHistogram(String metricName, int count, double sum) {
        assertThat(grpcServer.metricRequests.stream()
                .anyMatch(mReq -> mReq.getResourceMetricsList()
                        .stream()
                        .anyMatch(rm -> rm.getScopeMetricsList()
                                .stream()
                                .anyMatch(iml -> iml.getMetricsList()
                                        .stream()
                                        .anyMatch(metric -> metric.getName().equalsIgnoreCase(metricName)
                                                && metric.getHistogram().getDataPointsList()
                                                .stream()
                                                .anyMatch(dataPoint ->
                                                        dataPoint.getCount() == count && dataPoint.getSum() == sum
                                                )
                                        )
                                )
                        )
                )
        ).isTrue();
    }

    /**
     * Checks if a gauge with the given count and sum has been recorded
     *
     * @param value the expected value
     */
    protected void assertGauge(String metricName, int value) {
        assertThat(grpcServer.metricRequests.stream()
                .anyMatch(mReq -> mReq.getResourceMetricsList()
                        .stream()
                        .anyMatch(rm -> rm.getScopeMetricsList()
                                .stream()
                                .anyMatch(iml -> iml.getMetricsList()
                                        .stream()
                                        .anyMatch(metric -> metric.getName().equalsIgnoreCase(metricName) &&
                                                metric.getGauge().getDataPointsList()
                                                        .stream()
                                                .anyMatch(dataPoint -> dataPoint.getAsInt() == value)
                                        )
                                )
                        )
                )
        ).isTrue();
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
                        ExportTraceServiceRequest request = ExportTraceServiceRequest.parseFrom(message);
                        traceRequests.add(request);
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
                        ExportMetricsServiceRequest request = ExportMetricsServiceRequest.parseFrom(message);
                        metricRequests.add(request);
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
                        ExportLogsServiceRequest request = ExportLogsServiceRequest.parseFrom(message);
                        logRequests.add(request);
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
