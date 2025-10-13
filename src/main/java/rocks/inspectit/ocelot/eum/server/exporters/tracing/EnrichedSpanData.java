package rocks.inspectit.ocelot.eum.server.exporters.tracing;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.trace.data.DelegatingSpanData;
import io.opentelemetry.sdk.trace.data.SpanData;

/**
 * Wrapper class to add custom attributes - like resource attributes - to span data.
 */
public class EnrichedSpanData extends DelegatingSpanData {

    private final Attributes attributes;

    protected EnrichedSpanData(SpanData delegate, Attributes customAttributes) {
        super(delegate);
        this.attributes = Attributes.builder()
                .putAll(delegate.getAttributes())
                .putAll(customAttributes)
                .build();
    }

    @Override
    public Attributes getAttributes() {
        return attributes;
    }
}
