package rocks.inspectit.ocelot.eum.server.configuration.model.attributes.providers;

import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;

@Data
@NoArgsConstructor
public class AttributesProviderSettings {

    /**
     * The environment attributes providers.
     */
    @Valid
    private EnvironmentAttributesProviderSettings environment;
}
