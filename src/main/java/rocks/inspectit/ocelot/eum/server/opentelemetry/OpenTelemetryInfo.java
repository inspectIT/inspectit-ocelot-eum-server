package rocks.inspectit.ocelot.eum.server.opentelemetry;

import io.opentelemetry.sdk.common.InstrumentationScopeInfo;

/**
 * Constant information for OpenTelemetry
 */
public class OpenTelemetryInfo {

    public static final String INSTRUMENTATION_SCOPE_NAME = "rocks.inspectit.ocelot";

    public static final InstrumentationScopeInfo INSTRUMENTATION_SCOPE_INFO = InstrumentationScopeInfo.create(INSTRUMENTATION_SCOPE_NAME);
}
