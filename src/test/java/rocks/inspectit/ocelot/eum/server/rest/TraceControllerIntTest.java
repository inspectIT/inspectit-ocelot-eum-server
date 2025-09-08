package rocks.inspectit.ocelot.eum.server.rest;

import com.google.common.io.CharStreams;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import rocks.inspectit.ocelot.eum.server.exporters.tracing.DelegatingSpanExporter;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(SpringExtension.class)
public class TraceControllerIntTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Value("classpath:ot-trace-large-v0.48.0.json")
    private Resource resourceSpan;

    @Value("classpath:ot-trace-prod-v0.48.0.json")
    private Resource prodResourceSpans;

    @MockBean
    DelegatingSpanExporter spanExporter;

    @Captor
    ArgumentCaptor<Collection<SpanData>> spanCaptor;

    @Test
    public void empty() {
        ResponseEntity<Void> result = restTemplate.postForEntity("/spans", null, Void.class);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void badData() {
        ResponseEntity<Void> result = restTemplate.postForEntity("/spans", "{\"bad\": \"data'\"}", Void.class);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void verifyLargeTrace() throws Exception {
        try (Reader reader = new InputStreamReader(resourceSpan.getInputStream())) {
            String json = CharStreams.toString(reader);

            ResponseEntity<Void> result = restTemplate.postForEntity("/spans", json, Void.class);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

            verify(spanExporter).export(spanCaptor.capture());
            assertThat(spanCaptor.getValue()).hasSize(1).allSatisfy(data -> {
                assertThat(data.getTraceId()).isEqualTo("03c2a546267d1e90d70269bdc02babef");
                assertThat(data.getSpanId()).isEqualTo("c29e6dd2a1e1e7ae");
                assertThat(data.getParentSpanId()).isEqualTo("915c20356ab50086");
                assertThat(data.getKind()).isEqualTo(SpanKind.CLIENT);
                assertThat(data.getName()).isEqualTo("HTTP GET");
                assertThat(data.getStartEpochNanos()).isEqualTo(1619166153906575000L);
                assertThat(data.getEndEpochNanos()).isEqualTo(1619166154225390000L);
                assertThat(data.hasEnded()).isTrue();
                assertThat(data.getAttributes().asMap().keySet()).extracting(AttributeKey::getKey)
                        .containsExactlyInAnyOrder("client.ip", "http.response_content_length", "is.true", "http.method");
                assertThat(data.getEvents()).hasSize(1);
                assertThat(data.getLinks()).isEmpty();
            });
        }
    }

    @Test
    public void verifyTraceWithMultipleResourceSpans() throws Exception {
        try (Reader reader = new InputStreamReader(prodResourceSpans.getInputStream())) {
            String json = CharStreams.toString(reader);

            ResponseEntity<Void> result = restTemplate.postForEntity("/spans", json, Void.class);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            verify(spanExporter).export(spanCaptor.capture());
            assertThat(spanCaptor.getValue()).hasSize(2).allSatisfy(data -> {
                assertThat(data.getTraceId()).isEqualTo("a4a68b53c52438381b6cb304410ff0be");
                assertThat(data.getSpanId()).isNotBlank();
                assertThat(data.getName()).isNotBlank();
                assertThat(data.getKind()).isNotNull();
                assertThat(data.getAttributes()).isNotNull();
                assertThat(data.getEvents()).isNotNull();
                assertThat(data.getResource()).isNotNull();
                assertThat(data.hasEnded()).isTrue();
            });
        }
    }
}
