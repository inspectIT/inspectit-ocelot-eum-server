package rocks.inspectit.ocelot.eum.server.utils;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationObserver;

import java.io.File;
import java.io.FileFilter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DirectoryPoller {

    public static long DEFAULT_FREQUENCY_MS = 30000L;

    /**
     * @see FileAlterationObserver#FileAlterationObserver(File, FileFilter, IOCase)
     */
    private final File watchedDirectory;

    /**
     * @see FileAlterationObserver#FileAlterationObserver(File, FileFilter, IOCase)
     */
    private final FileFilter fileFilter;

    /**
     * @see FileAlterationObserver#FileAlterationObserver(File, FileFilter, IOCase)
     */
    private final IOCase ioCase;

    /**
     * Optional custom {@link  FileAlterationListener} if more fine-grained information about changed files are required.
     * In case only information if any file or directory is changed {@link DirectoryPoller#anyChangeDetectedCallback} is sufficient.
     */
    private final FileAlterationListener listener;

    /**
     * Internal {@link  FileAlterationListener} to track any file change. If a change is detected {@link DirectoryPoller#anyChangeDetectedCallback} is invoked.
     */
    private final AnyFileChangedListener anyFileChangedListener = new AnyFileChangedListener();

    /**
     * {@link Runnable} which is invoked if any file/directory change is detected by  {@link DirectoryPoller#anyFileChangedListener}
     */
    private final Runnable anyChangeDetectedCallback;

    /**
     * Frequency how often it should be checked for files changes
     */
    private final long frequencyInMillis;

    private final ScheduledExecutorService executor;

    private FileAlterationObserver observer;

    private ScheduledFuture<?> pollingTask;

    /**
     * Flag to indicate that the {@link #defaultExecutor()} is used, which is initialized by {@link  DirectoryPoller} itself
     * and needs to be shutdown on destroy.
     */
    private boolean isDefaultExecutor = false;

    /**
     * @param watchedDirectory          The directory to watch for changes
     * @param fileFilter                A {@link FileFilter} which files should be watched
     * @param ioCase                    A {@link IOCase} the be used while checking for changes
     * @param frequencyInMillis         Frequency how often folder should be checked
     * @param listener                  An optional custom listener to have a more fined-grained access which file changed
     * @param anyChangeDetectedCallback Simple Runnable which is invoked if any file/folder was chagned/created/deleted
     * @param executor                  An optional executor to schedule the polls
     */
    @Builder
    private DirectoryPoller(File watchedDirectory, FileFilter fileFilter, IOCase ioCase, long frequencyInMillis, FileAlterationListener listener, Runnable anyChangeDetectedCallback, ScheduledExecutorService executor) {
        if (!watchedDirectory.exists()) {
            throw new IllegalStateException("Directory must exists!");
        }
        this.fileFilter = fileFilter;
        this.ioCase = ioCase;
        this.listener = listener;
        this.watchedDirectory = watchedDirectory;
        this.executor = executor != null ? executor : defaultExecutor();
        this.frequencyInMillis = frequencyInMillis > 0 ? frequencyInMillis : DEFAULT_FREQUENCY_MS;
        this.anyChangeDetectedCallback = anyChangeDetectedCallback != null ? anyChangeDetectedCallback : () -> {
        };

        createObserver();
    }

    public DirectoryPoller start() {
        pollingTask = executor.scheduleWithFixedDelay(this::pollChanges, frequencyInMillis, frequencyInMillis, TimeUnit.MILLISECONDS);
        return this;
    }

    public void destroy() throws Exception {
        observer.destroy();
        pollingTask.cancel(true);
        if (isDefaultExecutor) {
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("Couldn't shut down Executor properly!");
            }
        }
    }

    public void pollChanges() {
        observer.checkAndNotify();
        if (anyFileChangedListener.isHasChanges()) {
            anyChangeDetectedCallback.run();
            anyFileChangedListener.reset();
        }
    }

    private void createObserver() {
        observer = new FileAlterationObserver(watchedDirectory, fileFilter, ioCase);
        if (listener != null) {
            observer.addListener(listener);
        }
        observer.addListener(anyFileChangedListener);
        try {
            observer.initialize();
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialize FileAlterationObserver!", e);
        }
    }

    private ScheduledExecutorService defaultExecutor() {
        isDefaultExecutor = true;
        return Executors.newScheduledThreadPool(1, (runnable) -> {
            Thread t = Executors.defaultThreadFactory().newThread(runnable);
            t.setDaemon(true);
            t.setName("inspectit-" + DirectoryPoller.class.getSimpleName());
            return t;
        });
    }

    @Getter
    private static class AnyFileChangedListener extends FileAlterationListenerAdaptor {

        private boolean hasChanges;

        private void reset() {
            hasChanges = false;
        }

        private void changeDetected() {
            hasChanges = true;
        }

        @Override
        public void onDirectoryCreate(File directory) {
            changeDetected();
        }

        @Override
        public void onDirectoryChange(File directory) {
            changeDetected();
        }

        @Override
        public void onDirectoryDelete(File directory) {
            changeDetected();
        }

        @Override
        public void onFileCreate(File file) {
            changeDetected();
        }

        @Override
        public void onFileChange(File file) {
            changeDetected();
        }

        @Override
        public void onFileDelete(File file) {
            changeDetected();
        }
    }
}
