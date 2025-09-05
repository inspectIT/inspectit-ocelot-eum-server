package rocks.inspectit.ocelot.eum.server.metrics;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import rocks.inspectit.ocelot.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.ocelot.eum.server.events.RegisteredAttributesEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.*;

@ExtendWith(MockitoExtension.class)
public class InstrumentManagerTest {

    @InjectMocks
    private InstrumentManager manager = new InstrumentManager();

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Captor
    private ArgumentCaptor<RegisteredAttributesEvent> eventArgumentCaptor;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private EumServerConfiguration configuration;

    @Nested
    class ProcessRegisteredTags {

        @Test
        void registerNoTag() {
            when(configuration.getTags().getExtra()).thenReturn(Collections.emptyMap());

            manager.processRegisteredAttributes(Collections.emptySet());

            verify(applicationEventPublisher).publishEvent(eventArgumentCaptor.capture());
            assertThat(eventArgumentCaptor.getValue().getRegisteredAttributes()).isEmpty();
            assertThat(manager.registeredExtraAttributes).isEmpty();
        }

        @Test
        void registerSingleTag() {
            when(configuration.getTags().getExtra()).thenReturn(Collections.singletonMap("first", "value"));

            manager.processRegisteredAttributes(Collections.singleton("first"));

            verify(applicationEventPublisher).publishEvent(eventArgumentCaptor.capture());
            assertThat(eventArgumentCaptor.getValue().getRegisteredAttributes()).containsExactly("first");
            assertThat(manager.registeredExtraAttributes).containsExactly("first");
        }

        @Test
        void registerMultipleTags() {
            Map<String, String> tagMap = ImmutableMap.of("first", "value", "second", "value");
            when(configuration.getTags().getExtra()).thenReturn(tagMap);

            manager.processRegisteredAttributes(Sets.newHashSet("first", "second"));

            verify(applicationEventPublisher).publishEvent(eventArgumentCaptor.capture());
            assertThat(eventArgumentCaptor.getValue().getRegisteredAttributes()).containsExactlyInAnyOrder("first", "second");
            assertThat(manager.registeredExtraAttributes).containsExactlyInAnyOrder("first", "second");
        }

        @Test
        void registerTagsMultipleTimes() {
            Map<String, String> tagMap = ImmutableMap.of("first", "value", "second", "value");
            when(configuration.getTags().getExtra()).thenReturn(tagMap);

            // first execution
            manager.processRegisteredAttributes(Collections.singleton("first"));

            assertThat(manager.registeredExtraAttributes).containsExactly("first");

            // second execution
            manager.processRegisteredAttributes(Collections.singleton("second"));

            assertThat(manager.registeredExtraAttributes).containsExactlyInAnyOrder("first", "second");
            verify(applicationEventPublisher, times(2)).publishEvent(eventArgumentCaptor.capture());
            RegisteredAttributesEvent eventOne = eventArgumentCaptor.getAllValues().get(0);
            RegisteredAttributesEvent eventTwo = eventArgumentCaptor.getAllValues().get(1);
            assertThat(eventOne.getRegisteredAttributes()).containsExactlyInAnyOrder("first");
            assertThat(eventTwo.getRegisteredAttributes()).containsExactlyInAnyOrder("first", "second");
        }
    }

}
