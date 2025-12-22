package com.comet.opik.infrastructure.auth;

import com.comet.opik.domain.ProjectService;
import com.comet.opik.utils.WorkspaceUtils;
import jakarta.inject.Provider;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.comet.opik.infrastructure.auth.RequestContext.WORKSPACE_HEADER;

/**
 * Basic authentication service that validates username and password from environment variables.
 * This provides a simple authentication mechanism for self-hosted installations.
 */
@RequiredArgsConstructor
@Slf4j
public class BasicAuthService implements AuthService {

    private static final String MISSING_CREDENTIALS = "Missing authentication credentials";
    private static final String INVALID_CREDENTIALS = "Invalid username or password";
    private static final String NOT_LOGGED_IN = "Please login first";

    private final @NonNull String adminUsername;
    private final @NonNull String adminPassword;
    private final @NonNull Provider<RequestContext> requestContext;

    // Simple in-memory session store (session token -> username)
    private final ConcurrentHashMap<String, String> sessions = new ConcurrentHashMap<>();

    @Override
    public void authenticate(HttpHeaders headers, Cookie sessionToken, ContextInfoHolder contextInfo) {
        var currentWorkspaceName = WorkspaceUtils.getWorkspaceName(headers.getHeaderString(WORKSPACE_HEADER));

        // Allow default workspace with session authentication
        if (ProjectService.DEFAULT_WORKSPACE_NAME.equals(currentWorkspaceName)) {
            if (sessionToken != null && sessions.containsKey(sessionToken.getValue())) {
                String username = sessions.get(sessionToken.getValue());
                requestContext.get().setUserName(username);
                requestContext.get().setWorkspaceId(ProjectService.DEFAULT_WORKSPACE_ID);
                requestContext.get().setWorkspaceName(ProjectService.DEFAULT_WORKSPACE_NAME);
                requestContext.get().setApiKey(sessionToken.getValue());
                return;
            }

            // Try basic auth header
            String authHeader = headers.getHeaderString(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith("Basic ")) {
                if (validateBasicAuth(authHeader)) {
                    requestContext.get().setUserName(adminUsername);
                    requestContext.get().setWorkspaceId(ProjectService.DEFAULT_WORKSPACE_ID);
                    requestContext.get().setWorkspaceName(ProjectService.DEFAULT_WORKSPACE_NAME);
                    requestContext.get().setApiKey("basic_auth");
                    return;
                }
            }

            throw new ClientErrorException(MISSING_CREDENTIALS, Response.Status.UNAUTHORIZED);
        }

        throw new ClientErrorException("Workspace not found", Response.Status.NOT_FOUND);
    }

    @Override
    public void authenticateSession(Cookie sessionToken) {
        if (sessionToken == null || StringUtils.isBlank(sessionToken.getValue())) {
            log.info("No session cookie found");
            throw new ClientErrorException(NOT_LOGGED_IN, Response.Status.UNAUTHORIZED);
        }

        if (!sessions.containsKey(sessionToken.getValue())) {
            log.info("Invalid session token");
            throw new ClientErrorException(NOT_LOGGED_IN, Response.Status.UNAUTHORIZED);
        }
    }

    /**
     * Validates basic auth credentials and creates a session
     * @param username Username to validate
     * @param password Password to validate
     * @return Session token if authentication succeeds
     */
    public Optional<String> login(String username, String password) {
        if (adminUsername.equals(username) && adminPassword.equals(password)) {
            String sessionToken = java.util.UUID.randomUUID().toString();
            sessions.put(sessionToken, username);
            log.info("User logged in successfully: {}", username);
            return Optional.of(sessionToken);
        }
        log.warn("Failed login attempt for user: {}", username);
        return Optional.empty();
    }

    /**
     * Removes a session
     * @param sessionToken Session token to invalidate
     */
    public void logout(String sessionToken) {
        if (sessionToken != null) {
            sessions.remove(sessionToken);
            log.info("User logged out");
        }
    }

    /**
     * Validates HTTP Basic Authentication header
     * @param authHeader Authorization header value
     * @return true if credentials are valid
     */
    private boolean validateBasicAuth(String authHeader) {
        try {
            String base64Credentials = authHeader.substring("Basic ".length()).trim();
            String credentials = new String(Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8);
            String[] parts = credentials.split(":", 2);

            if (parts.length == 2) {
                String username = parts[0];
                String password = parts[1];
                return adminUsername.equals(username) && adminPassword.equals(password);
            }
        } catch (Exception e) {
            log.warn("Failed to parse basic auth header", e);
        }
        return false;
    }
}
