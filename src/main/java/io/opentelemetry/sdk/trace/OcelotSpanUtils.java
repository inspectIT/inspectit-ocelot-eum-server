package io.opentelemetry.sdk.trace;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.BaseEncoding;
import com.google.protobuf.ByteString;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.internal.AttributesMap;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Utility class for creating {@link SpanData} instances from {@link Span} ones. This class is in the OpenTelemetry package
 * because it requires access to package-local classes, e.g. {@link SdkSpan}.
 */
@Slf4j
public class OcelotSpanUtils {

    /**
     * No-operation context.
     */
    private static final Context NOOP_CONTEXT = new Context() {
        @Override
        public <V> V get(ContextKey<V> key) {
            return null;
        }

        @Override
        public <V> Context with(ContextKey<V> k1, V v1) {
            return null;
        }
    };

    /**
     * No-operation span processor.
     */
    private static final SpanProcessor NOOP_SPAN_PROCESSOR = new SpanProcessor() {
        @Override
        public void onStart(Context parentContext, ReadWriteSpan span) {
        }

        @Override
        public boolean isStartRequired() {
            return false;
        }

        @Override
        public void onEnd(ReadableSpan span) {
        }

        @Override
        public boolean isEndRequired() {
            return false;
        }
    };

    /**
     * Creates a {@link SpanData} instance based on the given arguments.
     *
     * @param protoSpan                the protobuf representation of the span
     * @param resource                 the span's resources
     * @param instrumentationScopeInfo the information of the tracing library
     * @param customSpanAttributes     additional attributes which should be added to each span
     *
     * @return the created {@link SpanData} instance
     */
    public static SpanData createSpanData(Span protoSpan, Resource resource, InstrumentationScopeInfo instrumentationScopeInfo, Map<String, String> customSpanAttributes) {
        try {
            String traceId = toIdString(protoSpan.getTraceId());
            String spanId = toIdString(protoSpan.getSpanId());
            String parentSpanId = toIdString(protoSpan.getParentSpanId());

            SpanContext spanContext = createSpanContext(traceId, spanId);
            SpanContext parentSpanContext = createSpanContext(traceId, parentSpanId);
            io.opentelemetry.api.trace.Span parentSpan = io.opentelemetry.api.trace.Span.wrap(parentSpanContext);

            // only create spans with valid context
            if (!spanContext.isValid()) {
                return null;
            }

            // span data
            String name = protoSpan.getName();
            long startTime = protoSpan.getStartTimeUnixNano();
            SpanLimits spanLimits = SpanLimits.getDefault();
            int totalRecordedLinks = protoSpan.getLinksCount() + protoSpan.getDroppedLinksCount();
            SpanKind spanKind = toSpanKind(protoSpan.getKind());
            List<LinkData> links = toLinkData(protoSpan.getLinksList());

            // convert attributes map to AttributesMap
            Attributes spanAttributes = toAttributes(protoSpan.getAttributesList(), customSpanAttributes);
            Map<AttributeKey<?>, Object> attributesMap = spanAttributes.asMap();
            AttributesMap spanAttributesMap = AttributesMap.create(attributesMap.size(), spanLimits.getMaxAttributeValueLength());
            spanAttributesMap.putAll(attributesMap);

            // creating the actual span
            SdkSpan span = SdkSpan.startSpan(spanContext, name, instrumentationScopeInfo, spanKind, parentSpan, NOOP_CONTEXT, spanLimits, NOOP_SPAN_PROCESSOR, Clock.getDefault(), resource, spanAttributesMap, links, totalRecordedLinks, startTime);

            // add events to the span - and filter events which occurred before the actual span
            protoSpan.getEventsList()
                    .stream()
                    .filter(event -> event.getTimeUnixNano() >= span.getStartEpochNanos())
                    .forEach(event -> {
                        Attributes attributes = toAttributes(event.getAttributesList());
                        span.addEvent(event.getName(), attributes, event.getTimeUnixNano(), TimeUnit.NANOSECONDS);
                    });

            // the span's status code
            Status status = protoSpan.getStatus();
            StatusCode statusCode = toStatusCode(status.getCode());
            if (statusCode != null) {
                span.setStatus(statusCode, status.getMessage());
            }

            // set end time if available
            long endTime = protoSpan.getEndTimeUnixNano();
            if (endTime > 0) {
                span.end(endTime, TimeUnit.NANOSECONDS);
            }

            return span.toSpanData();
        } catch (Exception e) {
            log.warn("Error converting OT proto span {} to span data.", protoSpan, e);
            return null;
        }
    }

    /**
     * @return Creates a {@link SpanContext} based on the given IDs.
     */
    @VisibleForTesting
    static SpanContext createSpanContext(String traceId, String spanId) {
        //TODO - TraceState and TraceFlags are currently not supported by this class!
        if (!StringUtils.hasText(spanId)) {
            return SpanContext.getInvalid();
        }
        return SpanContext.create(traceId, spanId, TraceFlags.getDefault(), TraceState.getDefault());
    }

    /**
     * @return Returns the {@link StatusCode} which relates to the given {@link Status.StatusCode}.
     */
    @VisibleForTesting
    static StatusCode toStatusCode(Status.StatusCode code) {
        return switch (code) {
            case STATUS_CODE_UNSET -> StatusCode.UNSET;
            case STATUS_CODE_OK -> StatusCode.OK;
            case STATUS_CODE_ERROR -> StatusCode.ERROR;
            default -> null;
        };
    }

    /**
     * Builds a string representing an ID based on the content of the given {@link ByteString}. It is assumed that the
     * content of the given {@link ByteString} is already Base64 decoded - even the initial ID was not!
     *
     * @param bytes the {@link ByteString} containing an Base64 decoded ID.
     *
     * @return String representing an ID
     */
    private static String toIdString(ByteString bytes) {
        // the id has to be base64 encoded, because it is assumed that the received data is base64 encoded, which is not the case.
        // see: com.google.protobuf.util.JsonFormat.ParserImpl.parseBytes
        return BaseEncoding.base64().encode(bytes.toByteArray());
    }

    /**
     * @return Creates a list of {@link LinkData} from the given {@link Span.Link} list.
     */
    private static List<LinkData> toLinkData(List<Span.Link> linksList) {
        if (CollectionUtils.isEmpty(linksList)) {
            return Collections.emptyList();
        }

        return linksList.stream().map(link -> {
            SpanContext context = createSpanContext(toIdString(link.getSpanId()), toIdString(link.getTraceId()));
            Attributes attributes = toAttributes(link.getAttributesList());
            int totalAttributes = attributes.size() + link.getDroppedAttributesCount();

            return LinkData.create(context, attributes, totalAttributes);
        }).collect(Collectors.toList());
    }

    /**
     * @return Converts a {@link KeyValue} list into an {@link Attributes} instance.
     */
    public static Attributes toAttributes(List<KeyValue> attributesList) {
        return toAttributes(attributesList, Collections.emptyMap());
    }

    /**
     * Creates an {@link Attributes} instance based on the content of the given list and map. The elements in the given
     * map may override the attributes of the {@link KeyValue} list!
     *
     * @param attributesList   a list of {@link KeyValue} instances
     * @param customAttributes a map with additional elements which should be added to the attributes instance
     *
     * @return the created {@link Attributes} instance
     */
    public static Attributes toAttributes(List<KeyValue> attributesList, Map<String, String> customAttributes) {
        if (CollectionUtils.isEmpty(attributesList) && CollectionUtils.isEmpty(customAttributes)) {
            return Attributes.empty();
        }

        AttributesBuilder builder = Attributes.builder();

        if (!CollectionUtils.isEmpty(attributesList)) {
            for (KeyValue attribute : attributesList) {
                // skip invalid data
                if (attribute == null) continue;

                String key = attribute.getKey();
                AnyValue value = attribute.getValue();
                AnyValue.ValueCase valueCase = value.getValueCase();
                switch (valueCase) {
                    case STRING_VALUE ->  {
                        AttributeKey<String> attributeKey = AttributeKey.stringKey(key);
                        builder.put(attributeKey, value.getStringValue());
                    }
                    case BOOL_VALUE -> {
                        AttributeKey<Boolean> attributeKey = AttributeKey.booleanKey(key);
                        builder.put(attributeKey, value.getBoolValue());
                    }
                    case INT_VALUE -> {
                        AttributeKey<Long> attributeKey = AttributeKey.longKey(key);
                        builder.put(attributeKey, value.getIntValue());
                    }
                    case DOUBLE_VALUE -> {
                        AttributeKey<Double> attributeKey = AttributeKey.doubleKey(key);
                        builder.put(attributeKey, value.getDoubleValue());
                    }
                    case ARRAY_VALUE -> {
                        AttributeKey<List<String>> attributeKey = AttributeKey.stringArrayKey(key);
                        List<String> stringValues = value.getArrayValue().getValuesList().stream()
                                .map(OcelotSpanUtils::getValueAsString)
                                .toList();
                        builder.put(attributeKey, stringValues);
                    }
                }
            }
        }

        if (!CollectionUtils.isEmpty(customAttributes)) {
            customAttributes.forEach((key, value) -> builder.put(AttributeKey.stringKey(key), value));
        }

        return builder.build();
    }

    /**
     * @return Returns a {@link SpanKind} representing the given {@link Span.SpanKind} instance.
     */
    private static SpanKind toSpanKind(Span.SpanKind spanKind) {
        return switch (spanKind) {
            case SPAN_KIND_SERVER -> SpanKind.SERVER;
            case SPAN_KIND_CLIENT -> SpanKind.CLIENT;
            case SPAN_KIND_PRODUCER -> SpanKind.PRODUCER;
            case SPAN_KIND_CONSUMER -> SpanKind.CONSUMER;
            default ->
                // default value if we can not map
                    SpanKind.INTERNAL;
        };
    }

    /**
     * @return the {@link AnyValue} as string.
     */
    private static String getValueAsString(AnyValue value) {
        return switch (value.getValueCase()) {
            case STRING_VALUE -> value.getStringValue();
            case INT_VALUE -> String.valueOf(value.getIntValue());
            case DOUBLE_VALUE -> String.valueOf(value.getDoubleValue());
            case BOOL_VALUE -> String.valueOf(value.getBoolValue());
            default -> "";
        };
    }
}
