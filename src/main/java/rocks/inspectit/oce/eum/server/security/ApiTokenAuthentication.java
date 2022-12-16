package rocks.inspectit.oce.eum.server.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Collections;

/**
 * {@link Authentication} implementation for ApiToken Authentications
 */
public class ApiTokenAuthentication extends AbstractAuthenticationToken {

    /**
     * Default principal name for unauthorized users
     */
    private static final String UNAUTHORIZED_TOKEN_USER = "unauthorized_token_user";

    /**
     * The current authenticated principal. {@link ApiTokenAuthentication#UNAUTHORIZED_TOKEN_USER} if not yet authorized
     */
    private String principal = UNAUTHORIZED_TOKEN_USER;

    /**
     * The token used for authentication
     */
    private String token;

    /**
     * Creates an unauthenticated ApiTokenAuthentication instance
     *
     * @param token The token used for later authentication
     */
    public ApiTokenAuthentication(String token) {
        super(Collections.emptyList());
        this.token = token;
    }

    /**
     * Creates an authenticated ApiTokenAuthentication instance
     *
     * @param principal   The name of the authenticated principal
     * @param authorities List of {@link GrantedAuthority}s (TODO Currently not in use)
     */
    public ApiTokenAuthentication(String principal, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }
}
