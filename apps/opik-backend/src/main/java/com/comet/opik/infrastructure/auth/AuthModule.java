package com.comet.opik.infrastructure.auth;

import com.comet.opik.infrastructure.AuthenticationConfig;
import com.comet.opik.infrastructure.OpikConfiguration;
import com.google.common.base.Preconditions;
import com.google.inject.Provides;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RedissonReactiveClient;
import ru.vyarus.dropwizard.guice.module.support.DropwizardAwareModule;
import ru.vyarus.dropwizard.guice.module.yaml.bind.Config;

import java.util.Objects;

public class AuthModule extends DropwizardAwareModule<OpikConfiguration> {

    @Provides
    @Singleton
    public BasicAuthService basicAuthService(
            @Config("authentication") AuthenticationConfig config,
            @NonNull Provider<RequestContext> requestContext) {
        
        if (config.isEnabled() && "basic".equalsIgnoreCase(config.getMode())) {
            return new BasicAuthService(config.getBasicAuthUsername(), config.getBasicAuthPassword(), requestContext);
        }
        
        // Return a dummy instance if basic auth is not enabled
        return new BasicAuthService("", "", requestContext);
    }

    @Provides
    @Singleton
    public AuthService authService(
            @Config("authentication") AuthenticationConfig config,
            @NonNull Provider<RequestContext> requestContext,
            @NonNull RedissonReactiveClient redissonClient,
            BasicAuthService basicAuthService) {

        if (!config.isEnabled()) {
            return new AuthServiceImpl(requestContext);
        }

        // Check if basic auth mode is enabled
        if ("basic".equalsIgnoreCase(config.getMode())) {
            Preconditions.checkArgument(StringUtils.isNotBlank(config.getBasicAuthUsername()),
                    "The property authentication.basicAuthUsername must not be blank when basic auth is enabled");
            Preconditions.checkArgument(StringUtils.isNotBlank(config.getBasicAuthPassword()),
                    "The property authentication.basicAuthPassword must not be blank when basic auth is enabled");
            
            return basicAuthService;
        }

        // Default to remote authentication
        Objects.requireNonNull(config.getReactService(),
                "The property authentication.reactService.url is required when authentication is enabled");

        Preconditions.checkArgument(StringUtils.isNotBlank(config.getReactService().url()),
                "The property authentication.reactService.url must not be blank when authentication is enabled");

        var cacheService = config.getApiKeyResolutionCacheTTLInSec() > 0
                ? new AuthCredentialsCacheService(redissonClient, config.getApiKeyResolutionCacheTTLInSec())
                : new NoopCacheService();

        return new RemoteAuthService(client(), config.getReactService(), requestContext, cacheService);
    }

    public Client client() {
        return ClientBuilder.newClient();
    }
}
