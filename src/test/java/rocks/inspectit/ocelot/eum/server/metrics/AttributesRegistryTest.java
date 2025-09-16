package rocks.inspectit.ocelot.eum.server.metrics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rocks.inspectit.ocelot.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.ocelot.eum.server.configuration.model.metrics.definition.view.ViewDefinitionSettings;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AttributesRegistryTest {

    @InjectMocks
    AttributesRegistry registry;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    EumServerConfiguration configuration;

    @Test
    void registerNoAttributes() {
        ViewDefinitionSettings view = new ViewDefinitionSettings();
        view.setAttributes(Collections.emptyMap());

        registry.processAttributeKeysForView(view);

        assertThat(registry.getRegisteredAttributes()).isEmpty();
    }

    @Test
    void registerViewAttribute() {
        when(configuration.getAttributes().getExtra()).thenReturn(Collections.singletonMap("first", "value"));
        when(configuration.getAttributes().getDefineAsGlobal()).thenReturn(Collections.singleton("first"));
        registry.setGlobalAttributes();

        ViewDefinitionSettings view = new ViewDefinitionSettings();
        view.setAttributes(Map.of("first", true));

        registry.processAttributeKeysForView(view);

        assertThat(registry.getRegisteredAttributes()).containsExactly("first");
    }

    @Test
    void registerExtraAttributes() {
        Map<String, String> extraAttributes = Map.of("first", "value1", "second", "value2");
        when(configuration.getAttributes().getExtra()).thenReturn(extraAttributes);
        when(configuration.getAttributes().getDefineAsGlobal()).thenReturn(Set.of("first", "second"));
        registry.setGlobalAttributes();

        ViewDefinitionSettings view = new ViewDefinitionSettings();
        view.setAttributes(Collections.emptyMap());

        registry.processAttributeKeysForView(view);

        assertThat(registry.getRegisteredGlobalAttributes()).containsExactlyInAnyOrder("first", "second");
    }

    @Test
    void registerAttributesMultipleTimes() {
        when(configuration.getAttributes().getDefineAsGlobal()).thenReturn(Set.of("first", "second"));
        registry.setGlobalAttributes();

        ViewDefinitionSettings view1 = new ViewDefinitionSettings();
        view1.setAttributes(Map.of("first", true));
        ViewDefinitionSettings view2 = new ViewDefinitionSettings();
        view2.setAttributes(Map.of("second", true));

        // first execution
        registry.processAttributeKeysForView(view1);

        assertThat(registry.getRegisteredGlobalAttributes()).containsExactly("first");
        assertThat(registry.getRegisteredAttributes()).containsExactly("first");

        // second execution
        registry.processAttributeKeysForView(view2);

        assertThat(registry.getRegisteredGlobalAttributes()).containsExactlyInAnyOrder("first", "second");
        assertThat(registry.getRegisteredAttributes()).containsExactlyInAnyOrder("first", "second");
    }
}
