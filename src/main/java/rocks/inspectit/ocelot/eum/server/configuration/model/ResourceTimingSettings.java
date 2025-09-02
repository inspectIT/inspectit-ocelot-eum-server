package rocks.inspectit.ocelot.eum.server.configuration.model;

import lombok.Data;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Resource timing settings.
 */
@Data
@Validated
public class ResourceTimingSettings {

    /**
     * If resource timing is enabled or not.
     */
    private boolean enabled;

    /**
     * Specifies which tags should be used for this view.
     */
    private Map<@NotBlank String, @NotNull Boolean> tags;

}
