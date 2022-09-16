package rocks.inspectit.oce.eum.server.configuration.model.security.authProvider;

import lombok.Data;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;

@Data
@Validated
public class AuthenticationProviderSettings {


    @Valid
    private SimpleApiTokenAuthenticationProviderSettings simple;
}
