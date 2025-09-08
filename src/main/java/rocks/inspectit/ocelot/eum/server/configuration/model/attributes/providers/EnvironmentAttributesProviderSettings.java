package rocks.inspectit.ocelot.eum.server.configuration.model.attributes.providers;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class EnvironmentAttributesProviderSettings {

    /**
     * If providers is enabled.
     */
    private boolean enabled;

    /**
     * If true tries to resolve the host name using {@link java.net.InetAddress}.
     */
    private boolean resolveHostName;

    /**
     * If true tries to resolve the host address using {@link java.net.InetAddress}.
     */
    private boolean resolveHostAddress;

}
