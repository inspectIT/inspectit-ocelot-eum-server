package rocks.inspectit.ocelot.eum.server.metrics;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rocks.inspectit.ocelot.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.ocelot.eum.server.configuration.model.metrics.definition.view.ViewDefinitionSettings;

import java.util.Collections;
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

    /** * Configured global attributes */
    private final Set<String> globalAttributes = new HashSet<>();

    /** Configured extra attributes */
    private final Set<String> extraAttributes = new HashSet<>();

    /** Set of all registered global attributes */
    private final Set<String> registeredGlobalAttributes = new HashSet<>();

    /** Set of all registered attributes */
    private final Set<String> registeredAttributes = new HashSet<>();

    @PostConstruct
    void setGlobalAttributes() {
        globalAttributes.addAll(configuration.getAttributes().getDefineAsGlobal());
        extraAttributes.addAll(configuration.getAttributes().getExtra().keySet());

        // register global constant attributes
        globalAttributes.stream()
                .filter(extraAttributes::contains)
                .forEach(registeredGlobalAttributes::add);
    }

    /**
     * Processes all attributes, which are exposed for the given view.
     */
    public void processAttributeKeysForView(ViewDefinitionSettings viewSettings) {
        Set<String> attributes = getAttributeKeysForView(viewSettings);
        processRegisteredAttributes(attributes);
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

        attributes.addAll(registeredGlobalAttributes);

        return attributes;
    }

    /**
     * Register attributes and global attributes.
     *
     * @param attributes the registered attributes
     */
    private void processRegisteredAttributes(Set<String> attributes) {
        registeredAttributes.addAll(attributes);

        Set<String> newGlobalAttributes = registeredAttributes.stream()
                .filter(globalAttributes::contains)
                .collect(Collectors.toSet());

        registeredGlobalAttributes.addAll(newGlobalAttributes);
    }
}
