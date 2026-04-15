package org.guohai.javasqlweb.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Validate security-sensitive startup configuration.
 */
@Component
public class SecurityStartupValidator {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityStartupValidator.class);
    private static final String DEFAULT_SIGNKEY = "jsw";
    private static final String LEGACY_TLS_ENABLED_MESSAGE = "Legacy TLS compatibility is enabled. This should only be used for trusted internal MSSQL servers.";

    @Value("${project.signkey:jsw}")
    private String signKey;

    @Value("${project.auto-user-link-enabled:false}")
    private boolean autoUserLinkEnabled;

    @Value("${project.legacy-tls-enabled:false}")
    private boolean legacyTlsEnabled;

    @PostConstruct
    public void validate() {
        if (DEFAULT_SIGNKEY.equals(signKey)) {
            LOG.warn("project.signkey is still using the default value. Replace it before production use.");
        }
        if (autoUserLinkEnabled && (signKey == null || signKey.trim().isEmpty() || DEFAULT_SIGNKEY.equals(signKey))) {
            throw new IllegalStateException("project.auto-user-link-enabled=true requires a non-default project.signkey");
        }
        if (legacyTlsEnabled) {
            LOG.warn(LEGACY_TLS_ENABLED_MESSAGE);
        }
    }
}
