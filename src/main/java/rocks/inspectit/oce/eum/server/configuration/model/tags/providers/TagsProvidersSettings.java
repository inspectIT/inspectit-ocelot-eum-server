package rocks.inspectit.oce.eum.server.configuration.model.tags.providers;

import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;

@Data
@NoArgsConstructor
public class TagsProvidersSettings {

    /**
     * The environment tags providers.
     */
    @Valid
    private EnvironmentTagsProviderSettings environment;

}
