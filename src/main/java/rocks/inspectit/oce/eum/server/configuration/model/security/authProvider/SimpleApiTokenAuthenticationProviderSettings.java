package rocks.inspectit.oce.eum.server.configuration.model.security.authProvider;

import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import java.time.Duration;

@Data
@Validated
public class SimpleApiTokenAuthenticationProviderSettings {

    /**
     * Flag indicates if the {@link rocks.inspectit.oce.eum.server.security.authprovider.SimpleApiTokenAuthenticationProvider} should be enabled.
     */
    private boolean enabled;

    /**
     * Path to directory where token provider files can be loaded from.
     */
    private String tokenDirectory;

    /**
     * Duration how often {@link #tokenDirectory} should be checked for changes.
     */
    @DurationMin(millis = 1000)
    private Duration frequency;

    /**
     * Flag indicates if {@link #tokenDirectory} should be watched for changes.
     */
    private boolean watch;

    /**
     * Name of the default token provider file. If the file does not already exists in the tokenDirectory, it will be created.
     */
    private String defaultFileName;

    @AssertTrue(message = "tokenDirectory can not be null or empty if SimpleApiTokenAuthentication is enabled")
    public boolean isTokenDirectoryNotNullIfEnabled() {
        return !isEnabled() || (isEnabled() && StringUtils.hasText(tokenDirectory));
    }
}
