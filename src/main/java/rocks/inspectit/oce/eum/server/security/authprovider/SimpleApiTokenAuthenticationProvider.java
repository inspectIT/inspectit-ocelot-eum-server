package rocks.inspectit.oce.eum.server.security.authprovider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import rocks.inspectit.oce.eum.server.configuration.model.EumServerConfiguration;
import rocks.inspectit.oce.eum.server.security.ApiTokenAuthentication;
import rocks.inspectit.oce.eum.server.utils.DirectoryPoller;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Simple Api Token authentication implementation to enable authentication with pre shared tokens.
 * Tokens can be provided in different files which must reside in one directory.
 * Configuration is as follows:
 * <pre>
 * inspectit-eum-server:
 * ...
 *  security:
 *    ....
 *    auth-provider:
 *      simple:
 *        # Enable/Disable Provider
 *        enabled: true
 *        # Flag indicates if the directory should be watched for changes and tokens reloaded
 *        watch: true
 *        # How often directory should be watched for changes
 *        frequency: 60s
 *        # The directory where token files are stored. Empty by default to force users to provide one
 *        token-directory: ""
 *        # Flag indicates if a default token file should be created with an initial token
 *        default-file-name: "default-token-file.yaml"
 *    </pre>
 */
@Slf4j
@Component
@ConditionalOnExpression(value = "${inspectit-eum-server.security.enabled} and ${inspectit-eum-server.security.auth-provider.simple.enabled}")
public class SimpleApiTokenAuthenticationProvider implements AuthenticationProvider {

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    @Autowired
    private ScheduledExecutorService executor;

    @Autowired
    private EumServerConfiguration configuration;

    private File tokenDirectory;

    private DirectoryPoller directoryPoller;

    private Map<String, TokenPrincipal> knownTokens = new HashMap<>();

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        TokenPrincipal principal = knownTokens.get(((String) authentication.getCredentials()));
        if (principal != null) {
            return new ApiTokenAuthentication(principal.getName(), Collections.emptyList());
        }
        // Return unauthenticated Authentication object
        return authentication;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return ApiTokenAuthentication.class.isAssignableFrom(authentication);
    }

    @VisibleForTesting
    @PostConstruct
    void init() {
        tokenDirectory = new File(configuration.getSecurity().getAuthProvider().getSimple().getTokenDirectory());

        if (tokenDirectory.exists() && !tokenDirectory.isDirectory()) {
            throw new IllegalStateException("Not a directory <" + tokenDirectory.getAbsolutePath() + ">");
        }

        createTokenDirectory();
        createDefaultTokenProviderFile();
        startWatchingTokenDirectory();
        loadTokens();
    }

    private void startWatchingTokenDirectory() {
        if (configuration.getSecurity().getAuthProvider().getSimple().isWatch()) {
            log.debug("Creating DirectoryPoller to listen for token file changes.");
            try {

                long pollFrequency = configuration.getSecurity()
                        .getAuthProvider()
                        .getSimple()
                        .getFrequency()
                        .toMillis();

                directoryPoller = DirectoryPoller.builder()
                        .watchedDirectory(tokenDirectory)
                        .anyChangeDetectedCallback(this::loadTokens)
                        .executor(executor)
                        .frequencyInMillis(pollFrequency)
                        .ioCase(IOCase.SYSTEM)
                        .build()
                        .start();

            } catch (Exception e) {
                log.error("Failed to start DirectoryPoller. Tokens won't reload automatically!", e);
            }
        }
    }

    private void createDefaultTokenProviderFile() {
        String defaultFileName = configuration.getSecurity().getAuthProvider().getSimple().getDefaultFileName();
        if (StringUtils.hasText(defaultFileName)) {
            File file = new File(tokenDirectory.getAbsolutePath() + File.separator + defaultFileName);
            if (!file.exists()) {
                log.debug("Create initial token file: {}", file.getAbsolutePath());
                try {
                    List<TokenPrincipal> defaultTokens = Collections.singletonList(new TokenPrincipal("Default Application", UUID.randomUUID()
                            .toString()));
                    mapper.writeValue(file, defaultTokens);
                } catch (IOException e) {
                    log.error("Could not create token file file <" + file.getAbsoluteFile() + ">! No authorization will be performed!", e);
                }
            }
        }
    }

    private void createTokenDirectory() {
        if (!tokenDirectory.exists()) {
            boolean mkdirs = false;
            Throwable catchException = null;
            try {
                log.info("Create token config directory <{}>", tokenDirectory.getAbsolutePath());
                mkdirs = tokenDirectory.mkdirs();
            } catch (Throwable e) {
                catchException = e;
            }
            if (catchException != null || !mkdirs) {
                throw new IllegalStateException("Failed to create token config directory <" + tokenDirectory.getPath() + ">", catchException);
            }

        }
    }

    @PreDestroy
    private void destroy() {
        if (directoryPoller != null) {
            try {
                directoryPoller.destroy();
            } catch (Exception e) {
                log.error("Failed to destroy DirectoryPoller", e);
            }
        }
    }

    private void loadTokens() {
        try (Stream<Path> files = Files.walk(tokenDirectory.toPath()).filter(p -> !p.toFile().isDirectory())) {
            List<TokenPrincipal> allTokens = Lists.newArrayList();
            files.forEach(f -> {
                try {
                    log.info("Loading tokens from <{}>", f.getFileName());
                    List<TokenPrincipal> tokenPrincipals = mapper.readValue(f.toFile(), new TypeReference<List<TokenPrincipal>>() {
                    });
                    allTokens.addAll(tokenPrincipals);
                } catch (IOException e) {
                    // Do not print stack trace to avoid dumping tokens to log file
                    log.warn("Could not load tokens from <{}>!", f.getFileName());
                }
            });

            knownTokens = allTokens.stream()
                    .collect(Collectors.toMap(TokenPrincipal::getToken, p -> p, (p1, p2) -> p2));
        } catch (Exception e) {
            log.error("Failed to load tokens.", e);
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TokenPrincipal {

        String name;

        String token;

    }

}

