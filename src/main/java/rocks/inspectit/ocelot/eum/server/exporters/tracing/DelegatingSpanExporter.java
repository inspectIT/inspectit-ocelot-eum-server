package rocks.inspectit.ocelot.eum.server.exporters.tracing;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import lombok.AllArgsConstructor;

import java.util.Collection;

/**
 * Delegate class to insert additional logic to span exporters.
 */
@AllArgsConstructor
public class DelegatingSpanExporter implements SpanExporter {

    private final SpanExporter delegate;

    private final Attributes attributes;

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        Collection<SpanData> enrichedSpans = spans.stream()
                .map(this::enrichSpanData)
                .toList();
        return delegate.export(enrichedSpans);
    }

    @Override
    public CompletableResultCode flush() {
        return delegate.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
        return delegate.shutdown();
    }

    @Override
    public void close() {
        delegate.close();
    }

    /**
     * Adds additional data to the original span.
     */
    private SpanData enrichSpanData(SpanData span) {
        return new EnrichedSpanData(span, attributes);
    }
}
