package rocks.inspectit.ocelot.eum.server.opentelemetry;

import io.opentelemetry.api.metrics.*;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.*;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import io.opentelemetry.sdk.resources.Resource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import rocks.inspectit.ocelot.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.ocelot.eum.server.opentelemetry.metrics.ViewManager;
import rocks.inspectit.ocelot.eum.server.opentelemetry.resource.ResourceManager;

import java.util.Collection;

@Slf4j
@Component
public class OpenTelemetryController {

    public static final String INSTRUMENTATION_SCOPE_NAME = "rocks.inspectit.ocelot";

    @Autowired
    private EumServerConfiguration configuration;

    @Autowired
    private ResourceManager resourceManager;

    @Autowired
    private ViewManager viewManager;

    @Autowired(required = false)
    private Collection<MetricReader> metricReaders;

    private OpenTelemetrySdk openTelemetry;

    @PostConstruct
    synchronized void configureOpenTelemetry() {
        if(openTelemetry != null) {
            log.warn("OpenTelemetry already configured!");
            return;
        }

        SdkMeterProvider meterProvider = configureMeterProvider();

        // TODO We don't need any tracerProvider, since we only export received tracing data instead of recording it
        //  Right?
        openTelemetry = OpenTelemetrySdk.builder()
                .setMeterProvider(meterProvider)
                .build();
        // Using buildAndRegisterGlobal() is also possible, but makes cleaning in tests more cumbersome...
    }

    public Meter getMeter() {
        return openTelemetry.getMeter(INSTRUMENTATION_SCOPE_NAME);
    }

    /**
     * Configure the meter provider with registered metric readers, metric producers
     * and register metric views.
     *
     * @return the configured meter provider
     */
    private SdkMeterProvider configureMeterProvider() {
        Resource resource = resourceManager.getResource();
        SdkMeterProviderBuilder builder = SdkMeterProvider.builder().setResource(resource);

        if(!CollectionUtils.isEmpty(metricReaders)) {
            for (MetricReader metricReader : metricReaders) {
                builder.registerMetricReader(metricReader);
            }
        }

        // TODO Register MetricProducer for custom view (percentile, smoothed average, timewindow)
        //builder.registerMetricProducer()

        viewManager.registerViews(builder);

        return builder.build();
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
