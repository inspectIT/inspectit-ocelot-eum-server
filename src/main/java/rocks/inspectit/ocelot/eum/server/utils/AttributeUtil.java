package rocks.inspectit.ocelot.eum.server.utils;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;

public class AttributeUtil {

    /**
     * Converts the provided baggage data to attributes.
     *
     * @param baggage the baggage
     *
     * @return the baggage data as attributes
     */
    public static Attributes toAttributes(Baggage baggage) {
        AttributesBuilder builder = Attributes.builder();
        baggage.asMap()
                .forEach((key, entry) -> builder.put(key, entry.getValue()));
        return builder.build();
    }
}
