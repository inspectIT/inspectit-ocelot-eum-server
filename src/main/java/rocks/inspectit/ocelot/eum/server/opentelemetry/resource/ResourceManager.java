package rocks.inspectit.ocelot.eum.server.opentelemetry.resource;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.TelemetryAttributes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rocks.inspectit.ocelot.eum.server.AppStartupRunner;
import rocks.inspectit.ocelot.eum.server.configuration.model.EumServerConfiguration;

@Component
public class ResourceManager {

    @Autowired
    private EumServerConfiguration configuration;

    @Autowired
    private AppStartupRunner appStartupRunner;

    /**
     * @return the OpenTelemetry resources
     */
    public Resource getResource() {
        return Resource.create(Attributes.of(
                ServiceAttributes.SERVICE_NAME, configuration.getExporters().getServiceName(),
                TelemetryAttributes.TELEMETRY_SDK_LANGUAGE, "java",
                TelemetryAttributes.TELEMETRY_SDK_NAME, "opentelemetry",
                TelemetryAttributes.TELEMETRY_SDK_VERSION, appStartupRunner.getOpenTelemetryVersion(),
                AttributeKey.stringKey("inspectit.eum-server.version"), appStartupRunner.getServerVersion()
        ));
    }
}
