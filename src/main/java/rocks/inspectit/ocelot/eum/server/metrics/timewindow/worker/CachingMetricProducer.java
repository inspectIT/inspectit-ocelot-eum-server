package rocks.inspectit.ocelot.eum.server.metrics.timewindow.worker;

import com.google.common.annotations.VisibleForTesting;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricProducer;
import io.opentelemetry.sdk.resources.Resource;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rocks.inspectit.ocelot.eum.server.metrics.timewindow.TimeWindowViewManager;
import rocks.inspectit.ocelot.eum.server.metrics.timewindow.views.TimeWindowView;
import rocks.inspectit.ocelot.eum.server.opentelemetry.OpenTelemetryController;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

/**
 * A metric producer which caches time-window metrics for a specified amount of time.
 * The metric producer will be registered in OpenTelemetry via {@link OpenTelemetryController}. <br>
 * Computation of percentiles can be expensive.
 * For this reason we cache computed metrics for 1 second before recomputing them.
 * Otherwise, e.g. spamming F5 on the prometheus endpoint could lead to an increased CPU usage.
 */
@Component
public class CachingMetricProducer implements MetricProducer {

    @Autowired
    private TimeWindowViewManager viewManager;

    /**
     * The duration for which cached metrics are kept.
     */
    private final Duration cacheDuration = Duration.ofSeconds(1);

    /**
     * The timestamp when the metrics were computed the last time.
     */
    private Instant cacheTimestamp = Instant.now();

    private Collection<MetricData> cachedMetrics;

    @Override
    public Collection<MetricData> produce(Resource resource) {
        Instant now = Instant.now();

        if (cachedMetrics == null || (now.toEpochMilli() - cacheTimestamp.toEpochMilli()) > cacheDuration.toMillis()) {
            cachedMetrics = computeMetrics(resource);
            cacheTimestamp = now;
        }
        return cachedMetrics;
    }

    @VisibleForTesting
    Collection<MetricData> computeMetrics(Resource resource) {
        Instant now = Instant.now();
        Collection<TimeWindowView> views = viewManager.getAllViews();
        return views.stream()
                .flatMap(view -> view.computeMetrics(now, resource).stream())
                .toList();
    }
}
