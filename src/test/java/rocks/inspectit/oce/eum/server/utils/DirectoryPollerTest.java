package rocks.inspectit.oce.eum.server.utils;

import org.apache.commons.io.monitor.FileAlterationListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.ReflectionUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DirectoryPollerTest {

    @Mock
    ScheduledExecutorService executorMock;

    @Mock
    ScheduledFuture pollingTaskMock;

    @Mock
    FileAlterationListener listenerMock;

    @Mock
    Runnable changeCallbackMock;

    @Test
    void testExecutorStartedAndStopWithCustomExecutor(@TempDir File tempDir) throws Exception {
        DirectoryPoller poller = DirectoryPoller.builder().watchedDirectory(tempDir).executor(executorMock).build();

        when(executorMock.scheduleWithFixedDelay(any(), eq(DirectoryPoller.DEFAULT_FREQUENCY_MS), eq(DirectoryPoller.DEFAULT_FREQUENCY_MS), eq(TimeUnit.MILLISECONDS))).thenReturn(pollingTaskMock);

        poller.start();

        verify(executorMock).scheduleWithFixedDelay(any(), eq(DirectoryPoller.DEFAULT_FREQUENCY_MS), eq(DirectoryPoller.DEFAULT_FREQUENCY_MS), eq(TimeUnit.MILLISECONDS));

        poller.destroy();

        verify(pollingTaskMock).cancel(true);

    }

    @Test
    void testExecutorStartedAndStopWithInternalExecutor(@TempDir File tempDir) throws Exception {
        DirectoryPoller poller = DirectoryPoller.builder().watchedDirectory(tempDir).build();
        Field executorField = DirectoryPoller.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        ReflectionUtils.setField(executorField, poller, executorMock);

        when(executorMock.scheduleWithFixedDelay(any(), eq(DirectoryPoller.DEFAULT_FREQUENCY_MS), eq(DirectoryPoller.DEFAULT_FREQUENCY_MS), eq(TimeUnit.MILLISECONDS))).thenReturn(pollingTaskMock);
        poller.start();

        verify(executorMock).scheduleWithFixedDelay(any(), eq(DirectoryPoller.DEFAULT_FREQUENCY_MS), eq(DirectoryPoller.DEFAULT_FREQUENCY_MS), eq(TimeUnit.MILLISECONDS));
        poller.destroy();

        verify(pollingTaskMock).cancel(true);
        verify(executorMock).shutdown();
    }

    @Test
    void testCustomListenerInvoked(@TempDir File tempDir) throws Exception {
        DirectoryPoller poller = DirectoryPoller.builder()
                .watchedDirectory(tempDir)
                .listener(listenerMock)
                .executor(executorMock)
                .build();

        new File(tempDir + File.separator + "new_file.yml").createNewFile();
        new File(tempDir + File.separator + "new_file2.yml").createNewFile();
        new File(tempDir + File.separator + "directory").mkdirs();

        poller.pollChanges();

        verify(listenerMock).onStart(any());
        verify(listenerMock, times(2)).onFileCreate(any());
        verify(listenerMock, times(1)).onDirectoryCreate(any());
        verify(listenerMock).onStop(any());
    }

    @Test
    void testChangeCallbackInvoked(@TempDir File tempDir) throws Exception {
        DirectoryPoller poller = DirectoryPoller.builder()
                .watchedDirectory(tempDir)
                .anyChangeDetectedCallback(changeCallbackMock)
                .executor(executorMock)
                .build();

        new File(tempDir + File.separator + "new_file.yml").createNewFile();

        poller.pollChanges();

        verify(changeCallbackMock, times(1)).run();
    }

}