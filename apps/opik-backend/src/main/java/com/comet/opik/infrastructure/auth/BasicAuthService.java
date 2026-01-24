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
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.comet.opik.infrastructure.auth.RequestContext.WORKSPACE_HEADER;

/**
 * Basic authentication service that validates username and password from environment variables.
 * This provides a simple authentication mechanism for self-hosted installations.
 *
 * Supports multiple authentication methods:
 * - Session cookies (from UI login)
 * - HTTP Basic Auth header (username:password)
 * - Bearer token (API key for programmatic access - HIPAA compliant)
 *
 * IMPORTANT LIMITATIONS:
 * - Sessions are stored in-memory and will be lost on service restart
 * - Designed for single-user (admin) scenarios
 * - For production multi-user scenarios, consider using the cloud version or implementing robust auth
 */
@Slf4j
public class BasicAuthService implements AuthService {

    private static final String MISSING_CREDENTIALS = "Missing authentication credentials";
    private static final String INVALID_CREDENTIALS = "Invalid username or password";
    private static final String INVALID_API_KEY = "Invalid API key";
    private static final String NOT_LOGGED_IN = "Please login first";

    private final @NonNull String adminUsername;
    private final @NonNull String adminPassword;
    private final String apiKey; // nullable - API key for Bearer token auth
    private final @NonNull Provider<RequestContext> requestContext;

    // Simple in-memory session store (session token -> username)
    private final ConcurrentHashMap<String, String> sessions = new ConcurrentHashMap<>();

    public BasicAuthService(@NonNull String adminUsername, @NonNull String adminPassword,
                           String apiKey, @NonNull Provider<RequestContext> requestContext) {
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.apiKey = apiKey;
        this.requestContext = requestContext;
    }

    @Override
    public void authenticate(HttpHeaders headers, Cookie sessionToken, ContextInfoHolder contextInfo) {
        var currentWorkspaceName = WorkspaceUtils.getWorkspaceName(headers.getHeaderString(WORKSPACE_HEADER));

        // Allow default workspace with session authentication
        if (ProjectService.DEFAULT_WORKSPACE_NAME.equals(currentWorkspaceName)) {
            // Try session cookie first
            if (sessionToken != null && sessions.containsKey(sessionToken.getValue())) {
                String username = sessions.get(sessionToken.getValue());
                requestContext.get().setUserName(username);
                requestContext.get().setWorkspaceId(ProjectService.DEFAULT_WORKSPACE_ID);
                requestContext.get().setWorkspaceName(ProjectService.DEFAULT_WORKSPACE_NAME);
                requestContext.get().setApiKey(sessionToken.getValue());
                return;
            }

            // Try Authorization header (Bearer token or Basic auth)
            String authHeader = headers.getHeaderString(HttpHeaders.AUTHORIZATION);
            if (authHeader != null) {
                // Try Bearer token (API key) first
                if (authHeader.startsWith("Bearer ")) {
                    if (validateBearerToken(authHeader)) {
                        requestContext.get().setUserName(adminUsername);
                        requestContext.get().setWorkspaceId(ProjectService.DEFAULT_WORKSPACE_ID);
                        requestContext.get().setWorkspaceName(ProjectService.DEFAULT_WORKSPACE_NAME);
                        requestContext.get().setApiKey(apiKey);
                        return;
                    }
                    throw new ClientErrorException(INVALID_API_KEY, Response.Status.UNAUTHORIZED);
                }

                // Try Basic auth
                if (authHeader.startsWith("Basic ")) {
                    if (validateBasicAuth(authHeader)) {
                        requestContext.get().setUserName(adminUsername);
                        requestContext.get().setWorkspaceId(ProjectService.DEFAULT_WORKSPACE_ID);
                        requestContext.get().setWorkspaceName(ProjectService.DEFAULT_WORKSPACE_NAME);
                        requestContext.get().setApiKey("basic_auth");
                        return;
                    }
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
        // Use constant-time comparison to prevent timing attacks
        boolean usernameMatch = MessageDigest.isEqual(
                adminUsername.getBytes(StandardCharsets.UTF_8),
                username.getBytes(StandardCharsets.UTF_8));
        boolean passwordMatch = MessageDigest.isEqual(
                adminPassword.getBytes(StandardCharsets.UTF_8),
                password.getBytes(StandardCharsets.UTF_8));

        if (usernameMatch && passwordMatch) {
            String sessionToken = generateSecureToken();
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
     * Generates a cryptographically secure random session token
     * @return A secure random token encoded as base64
     */
    private String generateSecureToken() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] tokenBytes = new byte[32]; // 256 bits of entropy
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
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

    /**
     * Validates Bearer token (API key) authentication
     * Uses constant-time comparison to prevent timing attacks
     * @param authHeader Authorization header value
     * @return true if the API key is valid
     */
    private boolean validateBearerToken(String authHeader) {
        if (StringUtils.isBlank(apiKey)) {
            log.warn("Bearer token authentication attempted but no API key configured");
            return false;
        }

        try {
            String token = authHeader.substring("Bearer ".length()).trim();
            // Use constant-time comparison to prevent timing attacks
            return MessageDigest.isEqual(
                    apiKey.getBytes(StandardCharsets.UTF_8),
                    token.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Failed to parse bearer token", e);
        }
        return false;
    }
}
