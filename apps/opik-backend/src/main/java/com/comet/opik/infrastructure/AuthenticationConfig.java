package com.comet.opik.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AuthenticationConfig {

    public record UrlConfig(@Valid @JsonProperty @NotNull String url) {
    }

    @Valid @JsonProperty
    private boolean enabled;

    @Valid @JsonProperty
    private int apiKeyResolutionCacheTTLInSec;

    @Valid @JsonProperty
    private UrlConfig reactService;

    @Valid @JsonProperty
    private String mode = "remote"; // "remote" or "basic"

    @Valid @JsonProperty
    private String basicAuthUsername;

    @Valid @JsonProperty
    private String basicAuthPassword;

    @Valid @JsonProperty
    private String basicAuthApiKey;
}
