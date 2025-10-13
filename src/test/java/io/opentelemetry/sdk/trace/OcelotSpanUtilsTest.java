package io.opentelemetry.sdk.trace;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.ArrayValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.Status;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcelotSpanUtilsTest {

    @Nested
    class CreateSpanContext {

        @Test
        void validContext() {
            SpanContext result = OcelotSpanUtils.createSpanContext("03c2a546267d1e90d70269bdc02babef", "c29e6dd2a1e1e7ae");

            assertThat(result.isValid()).isTrue();
            assertThat(result.getTraceId()).isEqualTo("03c2a546267d1e90d70269bdc02babef");
            assertThat(result.getSpanId()).isEqualTo("c29e6dd2a1e1e7ae");
        }

        @Test
        void emptySpanId() {
            SpanContext result = OcelotSpanUtils.createSpanContext("03c2a546267d1e90d70269bdc02babef", "");

            assertThat(result.isValid()).isFalse();
        }
    }

    @Nested
    class ToStatusCode {

        @Test
        void toStatusCode() {
            assertThat(OcelotSpanUtils.toStatusCode(Status.StatusCode.STATUS_CODE_OK)).isEqualTo(StatusCode.OK);
            assertThat(OcelotSpanUtils.toStatusCode(Status.StatusCode.STATUS_CODE_ERROR)).isEqualTo(StatusCode.ERROR);
            assertThat(OcelotSpanUtils.toStatusCode(Status.StatusCode.STATUS_CODE_UNSET)).isEqualTo(StatusCode.UNSET);
            assertThat(OcelotSpanUtils.toStatusCode(Status.StatusCode.UNRECOGNIZED)).isNull();
        }
    }

    @Nested
    class ToAttributes {

        @Test
        void verifyEmptyArray() {
            Attributes attributes = OcelotSpanUtils.toAttributes(Collections.emptyList());

            assertTrue(attributes.isEmpty());
        }

        @Test
        void verifyNullKeyValue() {
            List<KeyValue> keyValues = Collections.singletonList(null);

            Attributes attributes = OcelotSpanUtils.toAttributes(keyValues);

            assertTrue(attributes.isEmpty());
        }

        @Test
        void verifyEmptyKeyValue() {
            KeyValue kv = KeyValue.newBuilder().build();
            List<KeyValue> keyValues = Collections.singletonList(kv);

            Attributes attributes = OcelotSpanUtils.toAttributes(keyValues);

            assertTrue(attributes.isEmpty());
        }

        @Test
        void verifyNoValue() {
            KeyValue kv = KeyValue.newBuilder()
                    .setKey("service.name")
                    .build();
            List<KeyValue> keyValues = Collections.singletonList(kv);

            Attributes attributes = OcelotSpanUtils.toAttributes(keyValues);

            assertTrue(attributes.isEmpty());
        }

        @Test
        void verifyNoKey() {
            KeyValue kv = KeyValue.newBuilder()
                    .setValue(AnyValue.newBuilder().setStringValue("frontend").build())
                    .build();
            List<KeyValue> keyValues = Collections.singletonList(kv);

            Attributes attributes = OcelotSpanUtils.toAttributes(keyValues);

            assertTrue(attributes.isEmpty());
        }

        @Test
        void verifyValidAttributes() {
            KeyValue kvString = KeyValue.newBuilder()
                    .setKey("service.name")
                    .setValue(AnyValue.newBuilder().setStringValue("frontend").build())
                    .build();
            KeyValue kvBool = KeyValue.newBuilder()
                    .setKey("browser.mobile")
                    .setValue(AnyValue.newBuilder().setBoolValue(false).build())
                    .build();
            KeyValue kvInt = KeyValue.newBuilder()
                    .setKey("content_length")
                    .setValue(AnyValue.newBuilder().setIntValue(1000L).build())
                    .build();
            KeyValue kvDouble = KeyValue.newBuilder()
                    .setKey("index")
                    .setValue(AnyValue.newBuilder().setDoubleValue(0.90).build())
                    .build();
            KeyValue empty = KeyValue.newBuilder().build();
            List<KeyValue> keyValues = List.of(kvString, kvBool, kvInt, kvDouble, empty);

            Attributes expected = Attributes.builder()
                    .put("service.name", "frontend")
                    .put("browser.mobile", false)
                    .put("content_length", 1000)
                    .put("index", 0.90)
                    .build();

            Attributes attributes = OcelotSpanUtils.toAttributes(keyValues);

            assertEquals(expected, attributes);
        }

        @Test
        void verifyArrayValue() {
            KeyValue kvArray = KeyValue.newBuilder()
                    .setKey("browser.brands")
                    .setValue(AnyValue.newBuilder().setArrayValue(
                            ArrayValue.newBuilder()
                                    .addValues(AnyValue.newBuilder()
                                            .setStringValue("Chrome")
                                            .build())
                                    .addValues(AnyValue.newBuilder()
                                            .setStringValue("Firefox")
                                            .build())
                                    .addValues(AnyValue.newBuilder()
                                            .setIntValue(100)
                                            .build())
                                    .build())
                            .build())
                    .build();
            List<KeyValue> keyValues = Collections.singletonList(kvArray);

            AttributeKey<List<String>> arrayKey = AttributeKey.stringArrayKey("browser.brands");
            Attributes expected = Attributes.builder()
                    .put(arrayKey, List.of("Chrome", "Firefox", "100"))
                    .build();

            Attributes attributes = OcelotSpanUtils.toAttributes(keyValues);

            assertEquals(expected, attributes);
        }
    }
}
