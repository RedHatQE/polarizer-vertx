package com.github.redhatqe.polarizer.verticles.http.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PolarizerVertConfig {
    @JsonProperty(required = true)
    private String host;
    @JsonProperty(required = true)
    private int port;

    // TODO: SSL passwords, keystore and truststore keys
    @JsonProperty(value="keystore-path")
    private String keystorePath;
    @JsonProperty(value="keystore-password")
    private String keystorePassword;
    @JsonProperty(value="truststore-password")
    private String truststorePassword;
}
