package rocks.inspectit.ocelot.eum.server.metrics.timewindow.worker;

import com.google.common.annotations.VisibleForTesting;
import io.opentelemetry.api.baggage.Baggage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rocks.inspectit.ocelot.eum.server.metrics.timewindow.TimeWindowViewManager;
import rocks.inspectit.ocelot.eum.server.metrics.timewindow.views.TimeWindowView;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Records time-window metrics asynchronously.
 * Recording observation takes amortized O(1) time.
 * However, the worst-case time of a recording is O(n), which is why we decouple the recording from the application threads.
 * This worker maintains a fixed-size queue of observations which are then added via {@link #record)}.
 */
@Slf4j
@Component
public class TimeWindowRecorder {

    private volatile boolean overflowLogged = false;

    @VisibleForTesting
    final ArrayBlockingQueue<MetricRecord> recordsQueue = new ArrayBlockingQueue<>(8096);

    /**
     * The interval for processing the {@link #recordsQueue}
     */
    private final Duration recordingInterval = Duration.ofMillis(500);

    private Future<?> recordingTask;

    @Autowired
    private TimeWindowViewManager viewManager;

    @Autowired
    private ScheduledExecutorService executorService;

    @PostConstruct
    void postConstruct() {
        Thread worker = new Thread(this::record);
        worker.setDaemon(true);
        worker.setName("time-window-recorder");

        recordingTask = executorService.scheduleWithFixedDelay(worker, recordingInterval.toMillis(), recordingInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Records an observation for a given metric, if it was registered via {@link TimeWindowViewManager}.
     *
     * @param metricName  the name of the metric, e.g. http/responsetime
     * @param value       the observation to record
     * @param baggage     the baggage to use
     */
    public synchronized void recordMetric(String metricName, double value, Baggage baggage) {
        if (viewManager.areAnyViewsRegistered(metricName)) {
            boolean success = recordsQueue.offer(new MetricRecord(metricName, value, Instant.now(), baggage));
            if (!success && !overflowLogged) {
                overflowLogged = true;
                log.warn("Metric for time-window views has been dropped because queue is full. This message will not be shown for further drops");
            }
        }
    }

    /**
     * Asynchronous recording via {@link #recordingTask}.
     */
    private void record() {
        try {
            MetricRecord record = recordsQueue.take();
            doRecord(record.metricName, record.value, record.time, record.baggage);
        } catch (InterruptedException e) {
            log.error("TimeWindowRecorder interrupted");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Error processing record: ", e);
        }
    }

    /**
     * Records the specified metric observation.
     *
     * @param metricName the name of the metric
     * @param value      the observed value
     * @param time       the timestamp of the observation
     * @param baggage    the baggage to use
     */
    private void doRecord(String metricName, double value, Instant time, Baggage baggage) {
        Collection<TimeWindowView> views = viewManager.getViews(metricName);
        if (views != null) {
            views.forEach(view -> view.insertValue(value, time, baggage));
        }
    }

    @PreDestroy
    void destroy() {
        recordingTask.cancel(true);
    }

    /**
     * Queued metric record.
     *
     * @param metricName the metric name
     * @param value the recorded value
     * @param time the time of recording
     * @param baggage the baggage to use
     */
    private record MetricRecord(String metricName, double value, Instant time, Baggage baggage) {}
}
