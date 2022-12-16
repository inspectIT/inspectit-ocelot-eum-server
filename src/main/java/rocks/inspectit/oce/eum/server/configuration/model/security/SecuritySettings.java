package rocks.inspectit.oce.eum.server.configuration.model.security;

import lombok.Data;
import org.springframework.validation.annotation.Validated;
import rocks.inspectit.oce.eum.server.configuration.model.security.authProvider.AuthenticationProviderSettings;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
@Validated
public class SecuritySettings {

    /**
     * Enable/Disable Security
     */
    private boolean enabled;

    /**
     * Name of authorization header
     */
    @NotEmpty
    private String authorizationHeader;

    /**
     * List of white listed urls which must not be secured
     */
    private List<String> permittedUrls;

    @Valid
    private AuthenticationProviderSettings authProvider;

}
