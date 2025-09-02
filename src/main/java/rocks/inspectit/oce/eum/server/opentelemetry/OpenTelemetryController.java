package rocks.inspectit.oce.eum.server.opentelemetry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.TelemetryAttributes;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rocks.inspectit.oce.eum.server.AppStartupRunner;
import rocks.inspectit.oce.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.oce.eum.server.exporters.MetricsExporterService;

import java.util.Collection;
import java.util.LinkedList;

@Slf4j
@Component
public class OpenTelemetryController {

    @Autowired
    private EumServerConfiguration configuration;

    @Autowired
    private AppStartupRunner appStartupRunner;

    private OpenTelemetrySdk openTelemetry;

    private final Collection<MetricsExporterService> metricsExporterServices = new LinkedList<>();

    @PostConstruct
    synchronized void configureOpenTelemetry() {
        if(openTelemetry != null) {
            log.warn("OpenTelemetry already configured!");
            return;
        }
        SdkMeterProvider meterProvider = configureMeterProvider();

        // We don't need any tracerProvider, since we only export received tracing data instead of recording it
        openTelemetry = OpenTelemetrySdk.builder()
                .setMeterProvider(meterProvider)
                .build();
        // Using buildAndRegisterGlobal() is also possible, but makes cleaning in tests more cumbersome...
    }

    public void addMetricsExporterService(MetricsExporterService service) {
        metricsExporterServices.add(service);
    }

    /**
     * Configure the meter provider with registered metric readers
     *
     * @return the configured meter provider
     */
    private SdkMeterProvider configureMeterProvider() {
        Resource resource = getResource();
        SdkMeterProviderBuilder builder = SdkMeterProvider.builder().setResource(resource);

        for (MetricsExporterService service : metricsExporterServices) {
            builder.registerMetricReader(service.getNewMetricReader());
        }

        return builder.build();
    }

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

    @PreDestroy
    public void shutdown() {
        if (null != openTelemetry) {
            log.info("Flushing pending OpenTelemetry data");
            openTelemetry.close();
            log.info("Flushing OpenTelemetry data completed");
        }
        openTelemetry = null;
    }
}
