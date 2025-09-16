package rocks.inspectit.ocelot.eum.server.opentelemetry;

import io.opentelemetry.api.metrics.*;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.*;
import io.opentelemetry.sdk.metrics.export.MetricProducer;
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


    @Autowired
    private EumServerConfiguration configuration;

    @Autowired
    private ResourceManager resourceManager;

    @Autowired
    private ViewManager viewManager;

    @Autowired(required = false)
    private Collection<MetricReader> metricReaders;

    @Autowired(required = false)
    private Collection<MetricProducer> metricProducers;

    /**
     * The configured OpenTelemetry SDK
     */
    private OpenTelemetrySdk openTelemetry;

    @PostConstruct
    synchronized void configureOpenTelemetry() {
        if(openTelemetry != null) {
            log.warn("OpenTelemetry already configured!");
            return;
        }

        SdkMeterProvider meterProvider = configureMeterProvider();

        // We don't need any tracerProvider, since we do not record traces via API here
        openTelemetry = OpenTelemetrySdk.builder()
                .setMeterProvider(meterProvider)
                .build();
        // Using buildAndRegisterGlobal() is also possible, but makes cleaning in tests more cumbersome...
    }

    /**
     * @return the Meter API to create metrics
     */
    public Meter getMeter() {
        Meter meter = openTelemetry.getMeter(OpenTelemetryInfo.INSTRUMENTATION_SCOPE_NAME);
        return meter; // For debugging
    }

    /**
     * Configure the meter provider with registered metric readers, producers and views.
     *
     * @return the configured meter provider
     */
    private SdkMeterProvider configureMeterProvider() {
        Resource resource = resourceManager.getResource();
        SdkMeterProviderBuilder builder = SdkMeterProvider.builder().setResource(resource);

        if(!CollectionUtils.isEmpty(metricReaders)) {
            for (MetricReader metricReader : metricReaders) {
                log.debug("Registering OpenTelemetry MetricReader: {}", metricReader);
                builder.registerMetricReader(metricReader);
            }
        }
        else log.info("OpenTelemetry has not registered any MetricReader! " +
                "Thus no metrics can be recorded. Enable at least one metrics exporter to record metrics");

        if(!CollectionUtils.isEmpty(metricProducers)) {
            for (MetricProducer metricProducer : metricProducers) {
                log.debug("Registering OpenTelemetry MetricProducer: {}", metricProducer);
                builder.registerMetricProducer(metricProducer);
            }
        }

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
