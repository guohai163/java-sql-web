package org.guohai.javasqlweb.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.guohai.javasqlweb.beans.*;
import org.guohai.javasqlweb.dao.OidcConfigDao;
import org.guohai.javasqlweb.dao.OidcLoginStateDao;
import org.guohai.javasqlweb.dao.UserManageDao;
import org.guohai.javasqlweb.util.PasswordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * OIDC + SSF 服务实现。
 * 使用 Java 17 HttpClient 与远端 OIDC Provider / SSF Transmitter 交互。
 * 令牌与事件存储在内存中 (重启丢失)。
 */
@Service
public class OidcSsfServiceImpl implements OidcSsfService {

    private static final Logger LOG = LoggerFactory.getLogger(OidcSsfServiceImpl.class);

    private final OidcConfigDao oidcConfigDao;
    private final OidcLoginStateDao oidcLoginStateDao;
    private final UserManageDao userManageDao;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String appDatabaseDialect;

    /** OIDC discovery 缓存 */
    private volatile Map<String, Object> oidcDiscovery;
    /** SSF discovery 缓存 */
    private volatile Map<String, Object> ssfDiscovery;

    /** 存储的令牌 */
    private volatile OidcTokenInfo storedTokens;
    /** 存储的用户信息 */
    private volatile OidcUserInfo storedUserInfo;

    /** 事件日志 (最多保留 500 条) */
    private final CopyOnWriteArrayList<SsfEvent> eventLog = new CopyOnWriteArrayList<>();
    private static final int MAX_EVENT_LOG = 500;
    /** OIDC state 有效期：10 分钟 */
    private static final long OIDC_STATE_EXPIRE_MS = 10 * 60 * 1000L;
    private static final String OIDC_SUB_COLUMN = "oidc_sub";
    private static final String STATE_USAGE_ADMIN = "admin";
    private static final String STATE_USAGE_LOGIN = "login";
    /** user_tb 是否已经具备 oidc_sub 列，避免每次登录都访问 information_schema */
    private volatile Boolean oidcSubColumnPresent;

    public OidcSsfServiceImpl(OidcConfigDao oidcConfigDao,
                              OidcLoginStateDao oidcLoginStateDao,
                              UserManageDao userManageDao,
                              JdbcTemplate jdbcTemplate,
                              ObjectMapper objectMapper,
                              @Value("${app.db.dialect:mysql}") String appDatabaseDialect) {
        this.oidcConfigDao = oidcConfigDao;
        this.oidcLoginStateDao = oidcLoginStateDao;
        this.userManageDao = userManageDao;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.appDatabaseDialect = normalizeAppDatabaseDialect(appDatabaseDialect);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private OidcConfigBean getDbConfig() {
        try {
            OidcConfigBean config = oidcConfigDao.getOidcConfig();
            if (config != null) {
                config.setConfigSource("database");
            }
            return config;
        } catch (Exception e) {
            LOG.warn("Failed to load OIDC config from DB", e);
            return null;
        }
    }

    private boolean isConfiguredAndEnabled(OidcConfigBean cfg) {
        return cfg != null
                && Boolean.TRUE.equals(cfg.getEnabled())
                && notBlank(cfg.getClientId())
                && notBlank(cfg.getClientSecret())
                && notBlank(cfg.getOpenidConfigurationUrl())
                && notBlank(cfg.getSsfConfigurationUrl());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String buildOrigin(HttpServletRequest request) {
        String proto = firstHeader(request, "X-Forwarded-Proto");
        String host = firstHeader(request, "X-Forwarded-Host");
        String port = firstHeader(request, "X-Forwarded-Port");

        if (notBlank(proto) && notBlank(host)) {
            String normalizedHost = host.split(",")[0].trim();
            String normalizedProto = proto.split(",")[0].trim();
            String normalizedPort = notBlank(port) ? port.split(",")[0].trim() : "";
            boolean defaultPort = ("http".equalsIgnoreCase(normalizedProto) && "80".equals(normalizedPort))
                    || ("https".equalsIgnoreCase(normalizedProto) && "443".equals(normalizedPort));
            if (notBlank(normalizedPort) && !defaultPort && !normalizedHost.contains(":")) {
                return normalizedProto + "://" + normalizedHost + ":" + normalizedPort;
            }
            return normalizedProto + "://" + normalizedHost;
        }

        String reqHost = request.getHeader("Host");
        if (!notBlank(reqHost)) {
            reqHost = request.getServerName();
            int reqPort = request.getServerPort();
            boolean defaultPort = ("http".equalsIgnoreCase(request.getScheme()) && reqPort == 80)
                    || ("https".equalsIgnoreCase(request.getScheme()) && reqPort == 443);
            if (!defaultPort && reqPort > 0) {
                reqHost = reqHost + ":" + reqPort;
            }
        }
        return request.getScheme() + "://" + reqHost;
    }

    private static String firstHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value == null ? "" : value;
    }

    private String buildAdminCallbackUrl(HttpServletRequest request) {
        return buildOrigin(request) + "/api/oidc/callback";
    }

    private String buildLoginCallbackUrl(HttpServletRequest request) {
        return buildOrigin(request) + "/api/oidc/login/callback";
    }

    // ════════════════════════════════════════════════════════
    //  OIDC Discovery
    // ════════════════════════════════════════════════════════

    private Map<String, Object> getOidcDiscovery(OidcConfigBean cfg) {
        if (oidcDiscovery != null) {
            return oidcDiscovery;
        }
        synchronized (this) {
            if (oidcDiscovery != null) {
                return oidcDiscovery;
            }
            try {
                String url = cfg.getOpenidConfigurationUrl();
                oidcDiscovery = httpGetJson(url);
                LOG.info("OIDC discovery loaded from {}", url);
            } catch (Exception e) {
                LOG.error("Failed to load OIDC discovery", e);
                oidcDiscovery = Map.of();
            }
        }
        return oidcDiscovery;
    }

    private Map<String, Object> getSsfDiscovery(OidcConfigBean cfg) {
        if (ssfDiscovery != null) {
            return ssfDiscovery;
        }
        synchronized (this) {
            if (ssfDiscovery != null) {
                return ssfDiscovery;
            }
            try {
                String url = cfg.getSsfConfigurationUrl();
                ssfDiscovery = httpGetJson(url);
                LOG.info("SSF discovery loaded from {}", url);
            } catch (Exception e) {
                LOG.error("Failed to load SSF discovery", e);
                ssfDiscovery = Map.of();
            }
        }
        return ssfDiscovery;
    }

    private String disc(OidcConfigBean cfg, String key) {
        Object v = getOidcDiscovery(cfg).get(key);
        return v != null ? v.toString() : "";
    }

    private String ssfDisc(OidcConfigBean cfg, String key) {
        Object v = getSsfDiscovery(cfg).get(key);
        return v != null ? v.toString() : "";
    }

    // ════════════════════════════════════════════════════════
    //  OIDC Authorization
    // ════════════════════════════════════════════════════════

    @Override
    public Map<String, String> buildAuthorizationUrl(HttpServletRequest request) {
        String state = generateRandomString(32);
        String codeVerifier = generateRandomString(64);
        String codeChallenge = computeS256Challenge(codeVerifier);
        String nonce = computeS256Challenge(codeVerifier);
        persistOidcState(STATE_USAGE_ADMIN, state, codeVerifier);

        OidcConfigBean effectiveConfig = getDbConfig();
        if (!isConfiguredAndEnabled(effectiveConfig)) {
            throw new IllegalStateException("OIDC config is not enabled");
        }
        String authEndpoint = disc(effectiveConfig, "authorization_endpoint");
        String scopes = "openid email profile ssf.manage ssf.read";
        String callbackUrl = buildAdminCallbackUrl(request);

        String url = authEndpoint
                + "?response_type=code"
                + "&client_id=" + enc(effectiveConfig.getClientId())
                + "&redirect_uri=" + enc(callbackUrl)
                + "&scope=" + enc(scopes)
                + "&state=" + enc(state)
                + "&code_challenge=" + enc(codeChallenge)
                + "&code_challenge_method=S256"
                + "&nonce=" + enc(nonce);

        return Map.of("authUrl", url, "state", state);
    }

    @Override
    @Transactional
    public Result<OidcTokenInfo> exchangeCodeForTokens(String code, String state, HttpServletRequest request) {
        String codeVerifier = consumeOidcState(STATE_USAGE_ADMIN, state);
        if (codeVerifier == null) {
            return new Result<>(false, "Invalid state parameter", null);
        }

        OidcConfigBean effectiveConfig = getDbConfig();
        if (!isConfiguredAndEnabled(effectiveConfig)) {
            return new Result<>(false, "OIDC config is not enabled", null);
        }
        String tokenEndpoint = disc(effectiveConfig, "token_endpoint");
        String callbackUrl = buildAdminCallbackUrl(request);
        String body = "grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(callbackUrl)
                + "&client_id=" + enc(effectiveConfig.getClientId())
                + "&client_secret=" + enc(effectiveConfig.getClientSecret())
                + "&code_verifier=" + enc(codeVerifier);

        try {
            Map<String, Object> tokenResponse = httpPostForm(tokenEndpoint, body);
            OidcTokenInfo tokens = parseTokenResponse(tokenResponse);

            // 验证 id_token 中的 nonce（从 code_verifier 派生期望值）
            String expectedNonce = computeS256Challenge(codeVerifier);
            if (!verifyIdTokenNonce(tokens.getIdToken(), expectedNonce)) {
                return new Result<>(false, "id_token nonce verification failed", null);
            }

            storedTokens = tokens;

            // 自动获取 userinfo
            try {
                fetchAndStoreUserInfo(tokens.getAccessToken());
            } catch (Exception e) {
                LOG.warn("Failed to fetch userinfo after token exchange", e);
            }

            return new Result<>(true, "OK", maskTokenInfo(tokens));
        } catch (Exception e) {
            LOG.error("Token exchange failed", e);
            return new Result<>(false, "Token exchange failed: " + e.getMessage(), null);
        }
    }

    @Override
    public Result<OidcTokenInfo> refreshTokens() {
        if (storedTokens == null || storedTokens.getRefreshToken() == null) {
            return new Result<>(false, "No refresh token available", null);
        }

        OidcConfigBean effectiveConfig = getDbConfig();
        if (!isConfiguredAndEnabled(effectiveConfig)) {
            return new Result<>(false, "OIDC config is not enabled", null);
        }
        String tokenEndpoint = disc(effectiveConfig, "token_endpoint");
        String body = "grant_type=refresh_token"
                + "&refresh_token=" + enc(storedTokens.getRefreshToken())
                + "&client_id=" + enc(effectiveConfig.getClientId())
                + "&client_secret=" + enc(effectiveConfig.getClientSecret());

        try {
            Map<String, Object> tokenResponse = httpPostForm(tokenEndpoint, body);
            OidcTokenInfo tokens = parseTokenResponse(tokenResponse);
            storedTokens = tokens;

            try {
                fetchAndStoreUserInfo(tokens.getAccessToken());
            } catch (Exception e) {
                LOG.warn("Failed to fetch userinfo after refresh", e);
            }

            return new Result<>(true, "Tokens refreshed", maskTokenInfo(tokens));
        } catch (Exception e) {
            LOG.error("Token refresh failed", e);
            return new Result<>(false, "Token refresh failed: " + e.getMessage(), null);
        }
    }

    @Override
    public Result<OidcUserInfo> getUserInfo() {
        if (storedUserInfo != null) {
            return new Result<>(true, "OK", storedUserInfo);
        }
        if (storedTokens == null) {
            return new Result<>(false, "Not connected", null);
        }
        try {
            fetchAndStoreUserInfo(storedTokens.getAccessToken());
            return new Result<>(true, "OK", storedUserInfo);
        } catch (Exception e) {
            return new Result<>(false, "Failed to fetch userinfo: " + e.getMessage(), null);
        }
    }

    @Override
    public Result<OidcTokenInfo> getStatus() {
        if (storedTokens == null) {
            OidcTokenInfo disconnected = OidcTokenInfo.builder().connected(false).build();
            return new Result<>(true, "Disconnected", disconnected);
        }
        return new Result<>(true, "Connected", maskTokenInfo(storedTokens));
    }

    @Override
    public Result<String> disconnect() {
        storedTokens = null;
        storedUserInfo = null;
        return new Result<>(true, "Disconnected", null);
    }

    // ════════════════════════════════════════════════════════
    //  SSF Stream Management
    // ════════════════════════════════════════════════════════

    @Override
    public Result<SsfStreamConfig> getSsfStream() {
        if (storedTokens == null) {
            return new Result<>(false, "Not connected to OIDC provider", null);
        }
        OidcConfigBean effectiveConfig = getDbConfig();
        if (!isConfiguredAndEnabled(effectiveConfig)) {
            return new Result<>(false, "OIDC config is not enabled", null);
        }
        String endpoint = ssfDisc(effectiveConfig, "configuration_endpoint");
        try {
            Map<String, Object> response = httpGetJsonAuth(endpoint, storedTokens.getAccessToken());
            return new Result<>(true, "OK", parseSsfStreamConfig(response));
        } catch (Exception e) {
            LOG.error("Failed to get SSF stream", e);
            return new Result<>(false, "Failed to get SSF stream: " + e.getMessage(), null);
        }
    }

    @Override
    public Result<SsfStreamConfig> createSsfStream(String endpointUrl, List<String> eventsRequested) {
        if (storedTokens == null) {
            return new Result<>(false, "Not connected to OIDC provider", null);
        }
        OidcConfigBean effectiveConfig = getDbConfig();
        if (!isConfiguredAndEnabled(effectiveConfig)) {
            return new Result<>(false, "OIDC config is not enabled", null);
        }
        String endpoint = ssfDisc(effectiveConfig, "configuration_endpoint");

        Map<String, Object> requestBody = new LinkedHashMap<>();
        Map<String, Object> delivery = new LinkedHashMap<>();
        delivery.put("method", "https://schemas.openid.net/secevent/risc/delivery-method/push");
        delivery.put("endpoint_url", endpointUrl);
        requestBody.put("delivery", delivery);
        if (eventsRequested != null && !eventsRequested.isEmpty()) {
            requestBody.put("events_requested", eventsRequested);
        }

        try {
            String json = objectMapper.writeValueAsString(requestBody);
            Map<String, Object> response = httpPostJsonAuth(endpoint, json, storedTokens.getAccessToken());
            return new Result<>(true, "Stream created", parseSsfStreamConfig(response));
        } catch (Exception e) {
            LOG.error("Failed to create SSF stream", e);
            return new Result<>(false, "Failed to create SSF stream: " + e.getMessage(), null);
        }
    }

    @Override
    public Result<SsfStreamConfig> updateSsfStream(String status, List<String> eventsRequested) {
        if (storedTokens == null) {
            return new Result<>(false, "Not connected to OIDC provider", null);
        }
        OidcConfigBean effectiveConfig = getDbConfig();
        if (!isConfiguredAndEnabled(effectiveConfig)) {
            return new Result<>(false, "OIDC config is not enabled", null);
        }
        String endpoint = ssfDisc(effectiveConfig, "configuration_endpoint");

        Map<String, Object> requestBody = new LinkedHashMap<>();
        if (status != null) {
            requestBody.put("status", status);
        }
        if (eventsRequested != null) {
            requestBody.put("events_requested", eventsRequested);
        }

        try {
            String json = objectMapper.writeValueAsString(requestBody);
            Map<String, Object> response = httpPatchJsonAuth(endpoint, json, storedTokens.getAccessToken());
            return new Result<>(true, "Stream updated", parseSsfStreamConfig(response));
        } catch (Exception e) {
            LOG.error("Failed to update SSF stream", e);
            return new Result<>(false, "Failed to update SSF stream: " + e.getMessage(), null);
        }
    }

    @Override
    public Result<String> deleteSsfStream() {
        if (storedTokens == null) {
            return new Result<>(false, "Not connected to OIDC provider", null);
        }
        OidcConfigBean effectiveConfig = getDbConfig();
        if (!isConfiguredAndEnabled(effectiveConfig)) {
            return new Result<>(false, "OIDC config is not enabled", null);
        }
        String endpoint = ssfDisc(effectiveConfig, "configuration_endpoint");
        try {
            httpDeleteAuth(endpoint, storedTokens.getAccessToken());
            return new Result<>(true, "Stream deleted", null);
        } catch (Exception e) {
            LOG.error("Failed to delete SSF stream", e);
            return new Result<>(false, "Failed to delete SSF stream: " + e.getMessage(), null);
        }
    }

    @Override
    public Result<String> requestVerification() {
        if (storedTokens == null) {
            return new Result<>(false, "Not connected to OIDC provider", null);
        }
        OidcConfigBean effectiveConfig = getDbConfig();
        if (!isConfiguredAndEnabled(effectiveConfig)) {
            return new Result<>(false, "OIDC config is not enabled", null);
        }
        String endpoint = ssfDisc(effectiveConfig, "verification_endpoint");
        try {
            Map<String, Object> requestBody = Map.of("state", generateRandomString(16));
            String json = objectMapper.writeValueAsString(requestBody);
            httpPostJsonAuth(endpoint, json, storedTokens.getAccessToken());
            return new Result<>(true, "Verification requested", null);
        } catch (Exception e) {
            LOG.error("Failed to request verification", e);
            return new Result<>(false, "Failed to request verification: " + e.getMessage(), null);
        }
    }

    @Override
    public Result<String> receiveSsfEvent(String setToken) {
        try {
            // SET 是一个 JWT，解析 payload (不做签名验证，仅展示用)
            String[] parts = setToken.split("\\.");
            if (parts.length < 2) {
                return new Result<>(false, "Invalid SET format", null);
            }
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> payload = objectMapper.readValue(payloadJson, new TypeReference<>() {});

            SsfEvent event = SsfEvent.builder()
                    .jti(strVal(payload, "jti"))
                    .iss(strVal(payload, "iss"))
                    .aud(strVal(payload, "aud"))
                    .receivedAt(Instant.now())
                    .rawPayload(payload)
                    .build();

            // 解析 iat
            Object iatObj = payload.get("iat");
            if (iatObj instanceof Number) {
                event.setIat(Instant.ofEpochSecond(((Number) iatObj).longValue()));
            }

            // 解析 events
            @SuppressWarnings("unchecked")
            Map<String, Object> events = (Map<String, Object>) payload.get("events");
            if (events != null && !events.isEmpty()) {
                String eventType = events.keySet().iterator().next();
                event.setEventType(eventType);

                @SuppressWarnings("unchecked")
                Map<String, Object> eventData = (Map<String, Object>) events.get(eventType);
                if (eventData != null && eventData.containsKey("subject")) {
                    event.setSubject(objectMapper.writeValueAsString(eventData.get("subject")));
                }
            }

            eventLog.add(0, event);
            while (eventLog.size() > MAX_EVENT_LOG) {
                eventLog.remove(eventLog.size() - 1);
            }

            LOG.info("Received SSF event: type={}, jti={}", event.getEventType(), event.getJti());
            return new Result<>(true, "Event received", null);
        } catch (Exception e) {
            LOG.error("Failed to process SSF event", e);
            return new Result<>(false, "Failed to process event: " + e.getMessage(), null);
        }
    }

    @Override
    public List<SsfEvent> getEventLog() {
        return List.copyOf(eventLog);
    }

    // ════════════════════════════════════════════════════════
    //  OIDC Config Management
    // ════════════════════════════════════════════════════════

    @Override
    public Result<OidcConfigBean> getOidcConfig(HttpServletRequest request) {
        OidcConfigBean effective = getDbConfig();
        if (effective == null) {
            return new Result<>(true, "OK", null);
        }
        effective.setCallbackUrl(buildAdminCallbackUrl(request));
        // 脱敏 secret
        if (effective.getClientSecret() != null && effective.getClientSecret().length() > 8) {
            effective.setClientSecret(
                    effective.getClientSecret().substring(0, 4) + "****"
                            + effective.getClientSecret().substring(effective.getClientSecret().length() - 4));
        }
        return new Result<>(true, "OK", effective);
    }

    @Override
    public Result<OidcConfigBean> saveOidcConfig(OidcConfigBean incoming) {
        try {
            if (!notBlank(incoming.getClientId())
                    || !notBlank(incoming.getClientSecret())
                    || !notBlank(incoming.getOpenidConfigurationUrl())
                    || !notBlank(incoming.getSsfConfigurationUrl())) {
                return new Result<>(false, "ClientId/Secret/OpenID URL/SSF URL 均为必填", null);
            }
            OidcConfigBean existing = oidcConfigDao.getOidcConfig();
            if (existing != null) {
                // 如果前端传的是脱敏 secret（含 ****），保留原值
                if (incoming.getClientSecret() != null && incoming.getClientSecret().contains("****")) {
                    incoming.setClientSecret(existing.getClientSecret());
                }
                incoming.setCode(existing.getCode());
                oidcConfigDao.updateOidcConfig(incoming);
            } else {
                if (incoming.getEnabled() == null) {
                    incoming.setEnabled(true);
                }
                oidcConfigDao.insertOidcConfig(incoming);
            }
            // 清除 discovery 缓存以重新加载
            clearDiscoveryCache();

            OidcConfigBean saved = oidcConfigDao.getOidcConfig();
            saved.setConfigSource("database");
            // 脱敏返回
            if (saved.getClientSecret() != null && saved.getClientSecret().length() > 8) {
                saved.setClientSecret(
                        saved.getClientSecret().substring(0, 4) + "****"
                                + saved.getClientSecret().substring(saved.getClientSecret().length() - 4));
            }
            return new Result<>(true, "配置已保存", saved);
        } catch (Exception e) {
            LOG.error("Failed to save OIDC config", e);
            return new Result<>(false, "保存失败: " + e.getMessage(), null);
        }
    }

    @Override
    public Result<String> deleteOidcConfig() {
        try {
            oidcConfigDao.deleteAllOidcConfig();
            clearDiscoveryCache();
            return new Result<>(true, "配置已删除，OIDC 登录已禁用", null);
        } catch (Exception e) {
            LOG.error("Failed to delete OIDC config", e);
            return new Result<>(false, "删除失败: " + e.getMessage(), null);
        }
    }

    @Override
    public Result<Map<String, Object>> testOidcConnection(String openidConfigurationUrl, String ssfConfigurationUrl) {
        if (!notBlank(openidConfigurationUrl) || !notBlank(ssfConfigurationUrl)) {
            return new Result<>(false, "OpenID/SSF Configuration URL 不能为空", null);
        }
        try {
            Map<String, Object> openidDiscovery = httpGetJson(openidConfigurationUrl);
            Map<String, Object> ssfDiscoveryDoc = httpGetJson(ssfConfigurationUrl);
            if (openidDiscovery.containsKey("authorization_endpoint") && ssfDiscoveryDoc.containsKey("configuration_endpoint")) {
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("issuer", openidDiscovery.get("issuer"));
                summary.put("authorization_endpoint", openidDiscovery.get("authorization_endpoint"));
                summary.put("token_endpoint", openidDiscovery.get("token_endpoint"));
                summary.put("userinfo_endpoint", openidDiscovery.get("userinfo_endpoint"));
                summary.put("ssf_configuration_endpoint", ssfDiscoveryDoc.get("configuration_endpoint"));
                summary.put("ssf_verification_endpoint", ssfDiscoveryDoc.get("verification_endpoint"));
                return new Result<>(true, "连接成功", summary);
            }
            return new Result<>(false, "Discovery 文档缺少关键字段", null);
        } catch (Exception e) {
            return new Result<>(false, "连接失败: " + e.getMessage(), null);
        }
    }

    private void clearDiscoveryCache() {
        this.oidcDiscovery = null;
        this.ssfDiscovery = null;
    }

    // ════════════════════════════════════════════════════════
    //  OIDC Login
    // ════════════════════════════════════════════════════════

    @Override
    public boolean isOidcLoginEnabled() {
        try {
            OidcConfigBean cfg = getDbConfig();
            return isConfiguredAndEnabled(cfg);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Map<String, String> buildLoginAuthorizationUrl(HttpServletRequest request) {
        String state = generateRandomString(32);
        String codeVerifier = generateRandomString(64);
        String codeChallenge = computeS256Challenge(codeVerifier);
        String nonce = computeS256Challenge(codeVerifier);
        persistOidcState(STATE_USAGE_LOGIN, state, codeVerifier);

        OidcConfigBean effectiveConfig = getDbConfig();
        if (!isConfiguredAndEnabled(effectiveConfig)) {
            throw new IllegalStateException("OIDC config is not enabled");
        }
        String authEndpoint = disc(effectiveConfig, "authorization_endpoint");
        String scopes = "openid email profile";
        String loginCallbackUrl = buildLoginCallbackUrl(request);

        String url = authEndpoint
                + "?response_type=code"
                + "&client_id=" + enc(effectiveConfig.getClientId())
                + "&redirect_uri=" + enc(loginCallbackUrl)
                + "&scope=" + enc(scopes)
                + "&state=" + enc(state)
                + "&code_challenge=" + enc(codeChallenge)
                + "&code_challenge_method=S256"
                + "&nonce=" + enc(nonce);

        return Map.of("authUrl", url, "state", state);
    }

    @Override
    @Transactional
    public Result<UserBean> handleLoginCallback(String code, String state, HttpServletRequest request) {
        String codeVerifier = consumeOidcState(STATE_USAGE_LOGIN, state);
        if (codeVerifier == null) {
            return new Result<>(false, "Invalid state parameter", null);
        }

        OidcConfigBean effectiveConfig = getDbConfig();
        if (!isConfiguredAndEnabled(effectiveConfig)) {
            return new Result<>(false, "OIDC config is not enabled", null);
        }
        String loginCallbackUrl = buildLoginCallbackUrl(request);

        // 1. 换取令牌
        String tokenEndpoint = disc(effectiveConfig, "token_endpoint");
        String body = "grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(loginCallbackUrl)
                + "&client_id=" + enc(effectiveConfig.getClientId())
                + "&client_secret=" + enc(effectiveConfig.getClientSecret())
                + "&code_verifier=" + enc(codeVerifier);

        Map<String, Object> tokenResponse;
        try {
            tokenResponse = httpPostForm(tokenEndpoint, body);
        } catch (Exception e) {
            LOG.error("OIDC login token exchange failed", e);
            return new Result<>(false, "Token exchange failed: " + e.getMessage(), null);
        }

        // 验证 id_token 中的 nonce（从 code_verifier 派生期望值）
        String idToken = strVal(tokenResponse, "id_token");
        String expectedNonce = computeS256Challenge(codeVerifier);
        if (!verifyIdTokenNonce(idToken, expectedNonce)) {
            return new Result<>(false, "id_token nonce verification failed", null);
        }

        String accessToken = strVal(tokenResponse, "access_token");
        if (accessToken == null || accessToken.isEmpty()) {
            return new Result<>(false, "No access_token in response", null);
        }

        // 2. 获取 userinfo
        Map<String, Object> userInfoData;
        try {
            String userinfoEndpoint = disc(effectiveConfig, "userinfo_endpoint");
            userInfoData = httpGetJsonAuth(userinfoEndpoint, accessToken);
        } catch (Exception e) {
            LOG.error("OIDC login userinfo failed", e);
            return new Result<>(false, "Failed to fetch userinfo: " + e.getMessage(), null);
        }

        String sub = strVal(userInfoData, "sub");
        String email = strVal(userInfoData, "email");
        String preferredUsername = strVal(userInfoData, "preferred_username");
        String name = strVal(userInfoData, "name");

        if (sub == null || sub.isBlank()) {
            return new Result<>(false, "OIDC provider did not return sub", null);
        }

        // 3. 查找或创建用户
        UserBean user = findOrCreateOidcUser(sub, email, preferredUsername, name);
        if (user == null) {
            return new Result<>(false, "Failed to create/find user", null);
        }

        // 4. 签发 JSW token，根据 OTP 状态决定登录步骤
        String jswToken = UUID.randomUUID().toString();

        if (user.getAuthStatus() == OtpAuthStatus.UNBIND || user.getAuthStatus() == OtpAuthStatus.BINDING) {
            // 未绑定 OTP → 生成 secret，进入 BINDING 流程
            GoogleAuthenticator gAuth = new GoogleAuthenticator();
            final GoogleAuthenticatorKey key = gAuth.createCredentials();
            user.setAuthSecret(key.getKey());
            userManageDao.setUserSecret(user.getAuthSecret(), jswToken, user.getUserName());
            user.setToken(jswToken);
            user.setAuthStatus(OtpAuthStatus.BINDING);
            LOG.info("OIDC login: sub={}, user={}, needs OTP binding", sub, user.getUserName());
            return new Result<>(true, "OIDC login needs OTP binding", user);
        }

        if (user.getAuthStatus() == OtpAuthStatus.BIND) {
            // 已绑定 OTP → 进入 VERIFY 流程（login_status=LOGGING）
            userManageDao.setUserToken(user.getUserName(), jswToken);
            user.setToken(jswToken);
            LOG.info("OIDC login: sub={}, user={}, needs OTP verification", sub, user.getUserName());
            return new Result<>(true, "OIDC login needs OTP verification", user);
        }

        // 其他状态：直接登录
        userManageDao.setUserToken(user.getUserName(), jswToken);
        userManageDao.setUserLoginSuccess(jswToken);
        user.setToken(jswToken);
        LOG.info("OIDC login successful: sub={}, user={}", sub, user.getUserName());
        return new Result<>(true, "OIDC login success", user);
    }

    /**
     * 将 OIDC state 持久化到数据库，支持多副本共享和重启恢复。
     * 这里会顺手清理历史过期数据，避免短期一次性记录无限增长。
     * @param usageType state 用途
     * @param state OIDC state
     * @param codeVerifier PKCE code_verifier
     */
    private void persistOidcState(String usageType, String state, String codeVerifier) {
        Date now = new Date();
        oidcLoginStateDao.deleteExpired(now);

        OidcLoginStateBean stateRecord = new OidcLoginStateBean();
        stateRecord.setUsageType(usageType);
        stateRecord.setStateKey(state);
        stateRecord.setCodeVerifier(codeVerifier);
        stateRecord.setCreatedTime(now);
        stateRecord.setExpireTime(new Date(now.getTime() + OIDC_STATE_EXPIRE_MS));
        if (!Boolean.TRUE.equals(oidcLoginStateDao.saveState(stateRecord))) {
            throw new IllegalStateException("Failed to persist OIDC state");
        }
    }

    /**
     * 以一次性方式消费 OIDC state。
     * 通过事务内先锁定再删除，确保多副本并发回调时只有一个请求可以成功消费。
     * @param usageType state 用途
     * @param state OIDC state
     * @return 对应的 code_verifier；不存在或已过期时返回 null
     */
    private String consumeOidcState(String usageType, String state) {
        Date now = new Date();
        OidcLoginStateBean stateRecord = oidcLoginStateDao.getActiveStateForUpdate(usageType, state, now);
        if (stateRecord == null) {
            oidcLoginStateDao.deleteExpiredByUsageAndState(usageType, state, now);
            return null;
        }
        Integer deleted = oidcLoginStateDao.deleteByCode(stateRecord.getCode());
        if (deleted == null || deleted < 1) {
            return null;
        }
        return stateRecord.getCodeVerifier();
    }

    /**
     * 按 sub → email → 创建 的优先级查找/创建用户
     */
    private UserBean findOrCreateOidcUser(String sub, String email, String preferredUsername, String displayName) {
        boolean hasOidcSubColumn = hasOidcSubColumn();

        // 1. 新结构优先按 oidc_sub 查找；旧结构则跳过这一层，避免直接打出 SQLSyntaxErrorException。
        UserBean user = null;
        if (hasOidcSubColumn) {
            user = userManageDao.getUserByOidcSub(sub);
            if (user != null) {
                LOG.info("OIDC login: found existing user by sub={}: {}", sub, user.getUserName());
                return user;
            }
        }

        // 2. 按 email 匹配；如果库结构已经升级，则顺带把 sub 绑定回去。
        if (email != null && !email.isBlank()) {
            user = userManageDao.getUserByEmail(email);
            if (user != null) {
                if (hasOidcSubColumn) {
                    userManageDao.updateOidcSub(user.getCode(), sub);
                    LOG.info("OIDC login: linked sub={} to existing user {} by email", sub, user.getUserName());
                } else {
                    LOG.warn("OIDC login: user_tb is missing oidc_sub, fallback to email match for user {}", user.getUserName());
                }
                return user;
            }
        }

        // 3. 自动创建新用户
        String userName = deriveUserName(preferredUsername, email, sub);
        if (email == null || email.isBlank()) {
            email = userName + "@oidc.local";
        }
        String randomPassword = PasswordUtils.encode(UUID.randomUUID().toString());

        try {
            if (hasOidcSubColumn) {
                userManageDao.addNewOidcUser(userName, email, randomPassword, sub);
                user = userManageDao.getUserByOidcSub(sub);
            } else {
                // 旧库结构先按普通用户创建，后续补列并执行迁移后即可绑定 sub。
                userManageDao.addNewUser(userName, email, randomPassword, "ACTIVE");
                user = userManageDao.getUserByEmail(email);
                if (user == null) {
                    user = userManageDao.getUserByName(userName);
                }
                LOG.warn("OIDC login: created fallback user {} without oidc_sub binding because column is missing", userName);
            }
            LOG.info("OIDC login: created new user {} for sub={}", userName, sub);
            return user;
        } catch (Exception e) {
            LOG.error("Failed to create OIDC user: {}", userName, e);
            return null;
        }
    }

    /**
     * 检查 user_tb 是否已经完成 oidc_sub 升级。
     * 结果会被缓存，只有首次访问或启动后才会查询 information_schema。
     * @return true 表示列存在
     */
    private boolean hasOidcSubColumn() {
        Boolean cached = oidcSubColumnPresent;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (oidcSubColumnPresent != null) {
                return oidcSubColumnPresent;
            }
            try {
                Integer count = jdbcTemplate.queryForObject(oidcSubColumnInspectionSql(), Integer.class, OIDC_SUB_COLUMN);
                oidcSubColumnPresent = count != null && count > 0;
            } catch (Exception e) {
                LOG.warn("Failed to inspect user_tb.oidc_sub, fallback to legacy-compatible OIDC login flow", e);
                oidcSubColumnPresent = false;
            }
            if (!oidcSubColumnPresent) {
                LOG.warn("user_tb is missing oidc_sub. OIDC login will run in legacy compatibility mode until DB migration is applied.");
            }
            return oidcSubColumnPresent;
        }
    }

    private String oidcSubColumnInspectionSql() {
        if ("postgresql".equals(appDatabaseDialect)) {
            return "SELECT COUNT(*) FROM information_schema.columns " +
                    "WHERE table_catalog = current_database() " +
                    "AND table_schema = current_schema() " +
                    "AND table_name = 'user_tb' AND column_name = ?";
        }
        return "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_schema = DATABASE() AND table_name = 'user_tb' AND column_name = ?";
    }

    private String normalizeAppDatabaseDialect(String dialect) {
        if (dialect == null) {
            return "mysql";
        }
        String normalized = dialect.trim().toLowerCase(Locale.ROOT);
        return ("postgres".equals(normalized) || "pgsql".equals(normalized) || "postgresql".equals(normalized))
                ? "postgresql"
                : "mysql";
    }

    /**
     * 从 OIDC userinfo 派生唯一用户名
     */
    private String deriveUserName(String preferredUsername, String email, String sub) {
        // 优先 preferred_username
        if (preferredUsername != null && !preferredUsername.isBlank()) {
            String candidate = preferredUsername.trim().toLowerCase().replaceAll("[^a-z0-9._-]", "_");
            if (!candidate.isEmpty() && userManageDao.getUserByName(candidate) == null) {
                return candidate;
            }
        }
        // 其次 email 前缀
        if (email != null && email.contains("@")) {
            String candidate = email.substring(0, email.indexOf('@')).trim().toLowerCase().replaceAll("[^a-z0-9._-]", "_");
            if (!candidate.isEmpty() && userManageDao.getUserByName(candidate) == null) {
                return candidate;
            }
        }
        // 最后用 sub 的前 16 位
        String candidate = "oidc_" + sub.replaceAll("[^a-zA-Z0-9]", "").substring(0, Math.min(sub.length(), 16)).toLowerCase();
        if (userManageDao.getUserByName(candidate) != null) {
            candidate = candidate + "_" + System.currentTimeMillis() % 10000;
        }
        return candidate;
    }

    // ════════════════════════════════════════════════════════
    //  Internal Helpers
    // ════════════════════════════════════════════════════════

    private void fetchAndStoreUserInfo(String accessToken) throws IOException, InterruptedException {
        OidcConfigBean cfg = getDbConfig();
        if (!isConfiguredAndEnabled(cfg)) {
            throw new IOException("OIDC config is not enabled");
        }
        String userinfoEndpoint = disc(cfg, "userinfo_endpoint");
        Map<String, Object> data = httpGetJsonAuth(userinfoEndpoint, accessToken);
        storedUserInfo = OidcUserInfo.builder()
                .sub(strVal(data, "sub"))
                .name(strVal(data, "name"))
                .familyName(strVal(data, "family_name"))
                .givenName(strVal(data, "given_name"))
                .email(strVal(data, "email"))
                .emailVerified(boolVal(data, "email_verified"))
                .preferredUsername(strVal(data, "preferred_username"))
                .build();
    }

    private OidcTokenInfo parseTokenResponse(Map<String, Object> response) {
        String accessToken = strVal(response, "access_token");
        String idToken = strVal(response, "id_token");
        String refreshToken = strVal(response, "refresh_token");
        String tokenType = strVal(response, "token_type");
        String scope = strVal(response, "scope");

        int expiresIn = 3600;
        Object exp = response.get("expires_in");
        if (exp instanceof Number) {
            expiresIn = ((Number) exp).intValue();
        }

        List<String> scopes = scope != null && !scope.isEmpty()
                ? Arrays.asList(scope.split("\\s+"))
                : List.of();

        return OidcTokenInfo.builder()
                .connected(true)
                .accessToken(accessToken)
                .idToken(idToken)
                .refreshToken(refreshToken)
                .tokenType(tokenType)
                .expiresAt(Instant.now().plusSeconds(expiresIn))
                .scopes(scopes)
                .build();
    }

    private OidcTokenInfo maskTokenInfo(OidcTokenInfo tokens) {
        return OidcTokenInfo.builder()
                .connected(tokens.isConnected())
                .accessToken(mask(tokens.getAccessToken()))
                .idToken(mask(tokens.getIdToken()))
                .refreshToken(null) // 不返回 refresh token 给前端
                .tokenType(tokens.getTokenType())
                .expiresAt(tokens.getExpiresAt())
                .scopes(tokens.getScopes())
                .build();
    }

    private String mask(String token) {
        if (token == null || token.length() <= 12) {
            return "***";
        }
        return token.substring(0, 6) + "..." + token.substring(token.length() - 6);
    }

    @SuppressWarnings("unchecked")
    private SsfStreamConfig parseSsfStreamConfig(Map<String, Object> data) {
        SsfStreamConfig cfg = SsfStreamConfig.builder()
                .streamId(strVal(data, "stream_id"))
                .issuer(strVal(data, "iss"))
                .status(strVal(data, "status"))
                .rawConfig(data)
                .build();

        Object aud = data.get("aud");
        if (aud instanceof List) {
            cfg.setAudience((List<String>) aud);
        } else if (aud instanceof String) {
            cfg.setAudience(List.of((String) aud));
        }

        Object delivery = data.get("delivery");
        if (delivery instanceof Map) {
            Map<String, Object> dm = (Map<String, Object>) delivery;
            cfg.setDeliveryMethod(strVal(dm, "method"));
            cfg.setEndpointUrl(strVal(dm, "endpoint_url"));
        }

        Object evReq = data.get("events_requested");
        if (evReq instanceof List) {
            cfg.setEventsRequested((List<String>) evReq);
        }

        Object evDel = data.get("events_delivered");
        if (evDel instanceof List) {
            cfg.setEventsDelivered((List<String>) evDel);
        }

        return cfg;
    }

    // ── HTTP helpers ───────────────────────────────────────

    private Map<String, Object> httpGetJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return objectMapper.readValue(response.body(), new TypeReference<>() {});
    }

    private Map<String, Object> httpGetJsonAuth(String url, String accessToken)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return objectMapper.readValue(response.body(), new TypeReference<>() {});
    }

    private Map<String, Object> httpPostForm(String url, String formBody) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("Token endpoint returned " + response.statusCode() + ": " + response.body());
        }
        return objectMapper.readValue(response.body(), new TypeReference<>() {});
    }

    private Map<String, Object> httpPostJsonAuth(String url, String jsonBody, String accessToken)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }
        String body = response.body();
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(body, new TypeReference<>() {});
    }

    private Map<String, Object> httpPatchJsonAuth(String url, String jsonBody, String accessToken)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }
        String body = response.body();
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(body, new TypeReference<>() {});
    }

    private void httpDeleteAuth(String url, String accessToken)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + accessToken)
                .DELETE()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    // ── Crypto helpers ─────────────────────────────────────

    private static String generateRandomString(int length) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, length);
    }

    private static String computeS256Challenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * 验证 id_token (JWT) 中的 nonce 是否与期望值一致。
     * nonce 从 code_verifier 派生 (SHA-256)，无需额外存储。
     * 仅解析 JWT payload，不做签名验证（签名安全性由 TLS + token endpoint 保证）。
     */
    private boolean verifyIdTokenNonce(String idToken, String expectedNonce) {
        if (idToken == null || idToken.isBlank()) {
            LOG.warn("No id_token returned, skipping nonce verification");
            return true;
        }
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length < 2) {
                LOG.warn("Invalid id_token format, skipping nonce verification");
                return true;
            }
            String payloadJson = new String(
                    Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> payload = objectMapper.readValue(
                    payloadJson, new TypeReference<>() {});
            String actualNonce = strVal(payload, "nonce");
            if (!expectedNonce.equals(actualNonce)) {
                LOG.error("id_token nonce mismatch: expected={}, actual={}",
                        expectedNonce, actualNonce);
                return false;
            }
            LOG.debug("id_token nonce verified successfully");
            return true;
        } catch (Exception e) {
            LOG.error("Failed to verify id_token nonce", e);
            return false;
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String strVal(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private static Boolean boolVal(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        return null;
    }
}
