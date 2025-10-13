package rocks.inspectit.ocelot.eum.server.metrics;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rocks.inspectit.ocelot.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.ocelot.eum.server.configuration.model.metrics.definition.view.ViewDefinitionSettings;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stores attribute keys for various purposes.
 */
@Component
@Getter
public class AttributesRegistry {

    @Autowired
    private EumServerConfiguration configuration;

    /** Set of all registered extra attribute keys */
    private final Set<String> registeredExtraAttributes = new HashSet<>();

    /** Set of all registered attribute keys */
    private final Set<String> registeredAttributes = new HashSet<>();

    /**
     * Processes all attributes, which are exposed for the given view.
     */
    public void processAttributeKeysForView(ViewDefinitionSettings viewSettings) {
        Set<String> attributes = getAttributeKeysForView(viewSettings);
        registeredAttributes.addAll(attributes);

        Set<String> extraAttributes = attributes.stream()
                .filter(configuration.getAttributes().getExtra()::containsKey)
                .collect(Collectors.toSet());
        registeredExtraAttributes.addAll(extraAttributes);
    }

    /**
     * @return the registered attribute keys for the provided view
     */
    public Set<String> getAttributeKeysForView(ViewDefinitionSettings viewSettings) {
        Set<String> attributes = viewSettings.getAttributes()
                .entrySet()
                .stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        attributes.addAll(configuration.getAttributes().getDefineAsGlobal());

        return attributes;
    }
}
