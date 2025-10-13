package rocks.inspectit.ocelot.eum.server.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * {@link Filter} implementation to extract an authorizationHeader from the {@link HttpServletRequest} and try to
 * authenticate the request by passing a {@link ApiTokenAuthentication} to {@link AuthenticationManager}.
 */
@Slf4j
public class ApiTokenFilter implements Filter {

    /**
     * The {@link AuthenticationManager} used to authenticate the request.
     * The AuthenticationManager is configured in {@link SecurityConfig}.
     */
    private final AuthenticationManager authenticationManager;

    /**
     * The name of the header which holds the authentication value
     */
    private final String authorizationHeader;

    /**
     * Flag indicates if this filter authenticated the request
     */
    private boolean authenticatedByFilter = false;

    public ApiTokenFilter(AuthenticationManager authenticationManager, String authorizationHeader) {
        this.authenticationManager = authenticationManager;
        this.authorizationHeader = authorizationHeader;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        String authorizationHeaderValue = httpServletRequest.getHeader(authorizationHeader);

        if (null != authorizationHeaderValue) {
            Authentication authentication = authenticationManager.authenticate(new ApiTokenAuthentication(authorizationHeaderValue));
            if (authentication.isAuthenticated()) {
                authenticatedByFilter = true;
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(servletRequest, servletResponse);
        if (authenticatedByFilter) {
            SecurityContextHolder.getContext().setAuthentication(null);
        }
    }
}
