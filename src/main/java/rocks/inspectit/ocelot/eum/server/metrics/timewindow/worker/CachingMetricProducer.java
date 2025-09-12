package rocks.inspectit.ocelot.eum.server.metrics.timewindow.worker;

import com.google.common.annotations.VisibleForTesting;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.MetricProducer;
import io.opentelemetry.sdk.resources.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rocks.inspectit.ocelot.eum.server.metrics.timewindow.TimeWindowViewManager;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

/**
 * A metric producer which caches time-window metrics for a specified amount of time. <br>
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
    private Instant cacheTimestamp;

    private Collection<MetricData> cachedMetrics = null;

    @Override
    public Collection<MetricData> produce(Resource resource) {
        Instant now = Instant.now();

        // TODO Do these times work as intended?
        if (cachedMetrics == null || (now.toEpochMilli() - cacheTimestamp.toEpochMilli()) > cacheDuration.toMillis()) {
            cachedMetrics = computeMetrics(resource);
            cacheTimestamp = now;
        }
        return cachedMetrics;
    }

    @VisibleForTesting
    Collection<MetricData> computeMetrics(Resource resource) {
        Instant now = Instant.now();
        return viewManager.getAllViews().stream()
                .flatMap(view -> view.computeMetrics(now, resource).stream())
                .toList();
    }
}
