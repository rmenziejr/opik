package com.comet.opik.api.resources.v1.session;

import com.codahale.metrics.annotation.Timed;
import com.comet.opik.api.error.ErrorMessage;
import com.comet.opik.infrastructure.AuthenticationConfig;
import com.comet.opik.infrastructure.auth.BasicAuthService;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Provider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;

import java.util.Optional;

import static com.comet.opik.infrastructure.auth.RequestContext.SESSION_COOKIE;

@Path("/v1/session/auth")
@Timed
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @jakarta.inject.Inject)
@Tag(name = "Authentication", description = "Basic authentication endpoints")
public class AuthenticationResource {

    public record LoginRequest(
            @JsonProperty("username") @NotBlank String username,
            @JsonProperty("password") @NotBlank String password) {
    }

    public record LoginResponse(
            @JsonProperty("success") boolean success,
            @JsonProperty("message") String message,
            @JsonProperty("username") String username) {
    }

    public record LogoutResponse(
            @JsonProperty("success") boolean success,
            @JsonProperty("message") String message) {
    }

    public record UserInfoResponse(
            @JsonProperty("loggedIn") boolean loggedIn,
            @JsonProperty("userName") String userName) {
    }

    private final @NonNull @Config("authentication") AuthenticationConfig authConfig;
    private final @NonNull Provider<BasicAuthService> basicAuthService;

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "login", summary = "Login with username and password", description = "Authenticate user and create session", responses = {
            @ApiResponse(responseCode = "200", description = "Login successful"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    })
    public Response login(@Valid @RequestBody(content = @Content(schema = @Schema(implementation = LoginRequest.class))) LoginRequest request) {
        if (!authConfig.isEnabled() || !"basic".equalsIgnoreCase(authConfig.getMode())) {
            log.warn("Basic authentication is not enabled");
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        log.info("Login attempt for user: {}", request.username());

        Optional<String> sessionToken = basicAuthService.get().login(request.username(), request.password());

        if (sessionToken.isPresent()) {
            NewCookie cookie = new NewCookie.Builder(SESSION_COOKIE)
                    .value(sessionToken.get())
                    .path("/")
                    .maxAge(86400) // 24 hours
                    .httpOnly(true)
                    .secure(false) // Set to true in production with HTTPS
                    .build();

            return Response.ok(new LoginResponse(true, "Login successful", request.username()))
                    .cookie(cookie)
                    .build();
        }

        log.warn("Login failed for user: {}", request.username());
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new LoginResponse(false, "Invalid username or password", null))
                .build();
    }

    @POST
    @Path("/logout")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "logout", summary = "Logout current user", description = "Invalidate session and logout", responses = {
            @ApiResponse(responseCode = "200", description = "Logout successful")
    })
    public Response logout(@CookieParam(SESSION_COOKIE) Cookie sessionCookie) {
        if (!authConfig.isEnabled() || !"basic".equalsIgnoreCase(authConfig.getMode())) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        if (sessionCookie != null) {
            basicAuthService.get().logout(sessionCookie.getValue());
        }

        // Clear the session cookie
        NewCookie expiredCookie = new NewCookie.Builder(SESSION_COOKIE)
                .value("")
                .path("/")
                .maxAge(0)
                .httpOnly(true)
                .build();

        return Response.ok(new LogoutResponse(true, "Logout successful"))
                .cookie(expiredCookie)
                .build();
    }

    @GET
    @Path("/user")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(operationId = "getCurrentUser", summary = "Get current user info", description = "Get information about the currently logged in user", responses = {
            @ApiResponse(responseCode = "200", description = "User info retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public Response getCurrentUser(@CookieParam(SESSION_COOKIE) Cookie sessionCookie) {
        if (!authConfig.isEnabled() || !"basic".equalsIgnoreCase(authConfig.getMode())) {
            // When auth is not enabled, return a default logged-in user
            return Response.ok(new UserInfoResponse(true, "admin")).build();
        }

        if (sessionCookie == null) {
            return Response.ok(new UserInfoResponse(false, null)).build();
        }

        try {
            basicAuthService.get().authenticateSession(sessionCookie);
            return Response.ok(new UserInfoResponse(true, authConfig.getBasicAuthUsername())).build();
        } catch (Exception e) {
            return Response.ok(new UserInfoResponse(false, null)).build();
        }
    }
}
