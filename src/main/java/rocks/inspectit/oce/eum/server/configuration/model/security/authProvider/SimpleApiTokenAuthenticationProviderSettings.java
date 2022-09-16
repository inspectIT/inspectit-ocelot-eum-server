package rocks.inspectit.oce.eum.server.configuration.model.security.authProvider;

import lombok.Data;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.NotEmpty;
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
    private String configDirectory;

    /**
     * Duration how often {@link #configDirectory} should be checked for changes.
     */
    @DurationMin(millis = 1000)
    private Duration frequency;

    /**
     * Flag indicates if {@link #configDirectory} should be watched for changes.
     */
    private boolean watch;

    /**
     * Name of the default token provider file if non exists and a {@link #createDefaultFileIfNotExists} is true.
     */
    @NotEmpty
    private String defaultFileName;

    private boolean createDefaultFileIfNotExists = true;

    @AssertTrue(message = "configDirectory can not be null or empty if SimpleApiTokenAuthentication is enabled")
    public boolean isConfigDirectoryNotNullifEnabled() {
        return !isEnabled() || (isEnabled() && StringUtils.hasText(configDirectory));
    }
}
