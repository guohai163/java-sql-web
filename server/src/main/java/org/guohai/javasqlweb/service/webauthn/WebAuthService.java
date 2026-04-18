package org.guohai.javasqlweb.service.webauthn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorAssertionResponse;
import com.yubico.webauthn.data.AuthenticatorAttestationResponse;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.ClientAssertionExtensionOutputs;
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import jakarta.servlet.http.HttpServletRequest;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.beans.UserLoginStatus;
import org.guohai.javasqlweb.beans.WebAuthnBean;
import org.guohai.javasqlweb.beans.WebAuthnRequestBean;
import org.guohai.javasqlweb.beans.WebAuthnRequestType;
import org.guohai.javasqlweb.dao.UserManageDao;
import org.guohai.javasqlweb.dao.WebAuthnDao;
import org.guohai.javasqlweb.dao.WebAuthnRequestDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * https://blog.csdn.net/qq_31650157/article/details/128938220
 */
@Service
public class WebAuthService {

    private static final Logger LOG = LoggerFactory.getLogger(WebAuthService.class);
    private static final String PASSKEY_REQUEST_INVALID_MESSAGE = "passkey 请求已失效，请重新发起";
    private static final String PASSKEY_ORIGIN_MISMATCH_MESSAGE =
            "passkey 域名配置不匹配，请检查 PROJECT_HOST 是否与浏览器访问地址完全一致";
    private static final long REQUEST_EXPIRE_MS = 5 * 60 * 1000L;

    RelyingParty rp;
    private final String relyingPartyDomain;
    private final String relyingPartyHost;

    /**
     * 管理DAO
     */
    @Autowired
    UserManageDao userDao;

    @Autowired
    WebAuthnDao webAuthnDao;

    @Autowired
    WebAuthnRequestDao webAuthnRequestDao;

    @Autowired
    HttpServletRequest httpServletRequest;

    public WebAuthService(@Value("${project.domain}") String domain,
                          @Value("${project.host}") String host,
                          MyCredentialRepository myCredentialRepository) {
        this.relyingPartyDomain = domain;
        this.relyingPartyHost = host;
        Set<String> setStr = new HashSet<>(2);
        setStr.add(host);

        RelyingPartyIdentity rpIdentity = RelyingPartyIdentity.builder()
                .id(domain)
                .name("Java Sql Web App")
                .build();
        rp = RelyingParty.builder()
                .identity(rpIdentity)
                .credentialRepository(myCredentialRepository)
                .origins(setStr)
                .build();
        logOriginConfigurationWarning();
    }

    /**
     * 创建CR时使用
     * @param token 登录态
     * @return 结果
     */
    @Transactional
    public Result<String> create(String token) {
        if (isBlank(token)) {
            return new Result<>(false, "未登录", null);
        }
        UserBean user = userDao.getUserByToken(token);
        if (user == null || UserLoginStatus.LOGGED != user.getLoginStatus()) {
            return new Result<>(false, "未登录", null);
        }

        UserIdentity userIdentity = UserIdentity.builder()
                .name(user.getUserName())
                .displayName(user.getUserName())
                .id(new ByteArray(user.getUserName().getBytes(StandardCharsets.UTF_8)))
                .build();

        PublicKeyCredentialCreationOptions request = startRegistrationRequest(userIdentity);

        try {
            return storeRequestAndBuildResult(WebAuthnRequestType.REGISTRATION,
                    token,
                    request.toJson(),
                    request.toCredentialsCreateJson(),
                    "创建CR成功");
        } catch (JsonProcessingException e) {
            LOG.error("Create passkey registration request failed", e);
            return new Result<>(false, "处理失败", null);
        }
    }

    /**
     * 存储passkey令牌
     * @param token 登录态
     * @param publicKeyCredentialJson 注册响应
     * @return 结果
     * @throws IOException JSON 解析异常
     */
    @Transactional
    public Result<String> register(String token, String publicKeyCredentialJson) throws IOException {
        if (isBlank(token)) {
            return new Result<>(false, "未登录", null);
        }
        if (isBlank(publicKeyCredentialJson)) {
            return new Result<>(false, "请求串为空", null);
        }

        WebAuthnRequestBean storedRequest = consumeStoredRequest(WebAuthnRequestType.REGISTRATION, token);
        if (storedRequest == null) {
            return new Result<>(false, PASSKEY_REQUEST_INVALID_MESSAGE, null);
        }

        PublicKeyCredentialCreationOptions request = PublicKeyCredentialCreationOptions.fromJson(storedRequest.getRequestJson());
        try {
            RegistrationVerificationResult result = verifyRegistration(publicKeyCredentialJson, request);
            UserBean user = userDao.getUserByToken(token);
            if (user == null) {
                return new Result<>(false, "未登录", null);
            }

            WebAuthnBean webAuthnBean = new WebAuthnBean(user.getUserName(),
                    result.userHandle(),
                    result.credentialId(),
                    result.publicKey(),
                    httpServletRequest.getHeader("User-Agent"),
                    new Date());
            return new Result<>(Boolean.TRUE.equals(webAuthnDao.addPublicKey(webAuthnBean)), "", null);
        } catch (RegistrationFailedException e) {
            LOG.error("Passkey registration failed. configuredDomain={}, configuredHost={}, allowedOrigins={}, message={}",
                    relyingPartyDomain, relyingPartyHost, rp.getOrigins(), e.getMessage(), e);
            return new Result<>(false, resolvePasskeyFailureMessage(e), null);
        }
    }

    /**
     * 登录准备
     * @param sessionKey 浏览器临时 key
     * @return 结果
     * @throws JsonProcessingException JSON 序列化异常
     */
    @Transactional
    public Result<String> get(String sessionKey) throws JsonProcessingException {
        if (isBlank(sessionKey)) {
            return new Result<>(false, PASSKEY_REQUEST_INVALID_MESSAGE, null);
        }
        AssertionRequest request = startAssertionRequest();
        return storeRequestAndBuildResult(WebAuthnRequestType.ASSERTION,
                sessionKey,
                request.toJson(),
                request.toCredentialsGetJson(),
                "成功");
    }

    /**
     * passkey 登录
     * @param publicKeyCredentialJson 断言响应
     * @param sessionKey 浏览器临时 key
     * @return 登录结果
     * @throws IOException JSON 解析异常
     */
    @Transactional
    public Result<UserBean> signIn(String publicKeyCredentialJson, String sessionKey) throws IOException {
        if (isBlank(sessionKey)) {
            return new Result<>(false, PASSKEY_REQUEST_INVALID_MESSAGE, null);
        }
        if (isBlank(publicKeyCredentialJson)) {
            return new Result<>(false, "请求串为空", null);
        }

        WebAuthnRequestBean storedRequest = consumeStoredRequest(WebAuthnRequestType.ASSERTION, sessionKey);
        if (storedRequest == null) {
            return new Result<>(false, PASSKEY_REQUEST_INVALID_MESSAGE, null);
        }

        AssertionRequest request = AssertionRequest.fromJson(storedRequest.getRequestJson());
        try {
            AssertionVerificationResult result = verifyAssertion(publicKeyCredentialJson, request);
            if (result.success()) {
                LOG.info(result.userName());
                UserBean user = userDao.getUserByName(result.userName());
                if (user == null) {
                    return new Result<>(false, "登录失败", null);
                }
                user.setToken(UUID.randomUUID().toString());
                if (Boolean.TRUE.equals(userDao.setUserToken(user.getUserName(), user.getToken()))) {
                    userDao.setUserLoginSuccess(user.getToken());
                    return new Result<>(true, "success", user);
                }
            }
        } catch (AssertionFailedException e) {
            LOG.error("Passkey sign in failed. configuredDomain={}, configuredHost={}, allowedOrigins={}, message={}",
                    relyingPartyDomain, relyingPartyHost, rp.getOrigins(), e.getMessage(), e);
            return new Result<>(false, e.toString(), null);
        }

        return new Result<>(false, "登录失败", null);
    }

    AssertionRequest startAssertionRequest() {
        return rp.startAssertion(StartAssertionOptions.builder()
                .build());
    }

    PublicKeyCredentialCreationOptions startRegistrationRequest(UserIdentity userIdentity) {
        return rp.startRegistration(StartRegistrationOptions.builder()
                .user(userIdentity)
                .build());
    }

    AssertionVerificationResult verifyAssertion(String publicKeyCredentialJson, AssertionRequest request)
            throws IOException, AssertionFailedException {
        PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> pkc =
                PublicKeyCredential.parseAssertionResponseJson(publicKeyCredentialJson);
        AssertionResult result = rp.finishAssertion(FinishAssertionOptions.builder()
                .request(request)
                .response(pkc)
                .build());
        return new AssertionVerificationResult(result.isSuccess(), result.getUsername());
    }

    RegistrationVerificationResult verifyRegistration(String publicKeyCredentialJson,
                                                      PublicKeyCredentialCreationOptions request)
            throws IOException, RegistrationFailedException {
        PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> pkc =
                PublicKeyCredential.parseRegistrationResponseJson(publicKeyCredentialJson);
        RegistrationResult result = rp.finishRegistration(FinishRegistrationOptions.builder()
                .request(request)
                .response(pkc)
                .build());
        return new RegistrationVerificationResult(request.getUser().getId().getBase64(),
                result.getKeyId().getId().getBase64(),
                result.getPublicKeyCose().getBase64());
    }

    private Result<String> storeRequestAndBuildResult(WebAuthnRequestType requestType,
                                                      String requestKey,
                                                      String requestJson,
                                                      String responseJson,
                                                      String successMessage) {
        Date now = new Date();
        webAuthnRequestDao.deleteByTypeAndKey(requestType, requestKey);
        webAuthnRequestDao.deleteExpired(now);

        WebAuthnRequestBean request = new WebAuthnRequestBean();
        request.setRequestType(requestType);
        request.setRequestKey(requestKey);
        request.setRequestJson(requestJson);
        request.setCreatedTime(now);
        request.setExpireTime(new Date(now.getTime() + REQUEST_EXPIRE_MS));
        if (!Boolean.TRUE.equals(webAuthnRequestDao.saveRequest(request))) {
            return new Result<>(false, "处理失败", null);
        }
        return new Result<>(true, successMessage, responseJson);
    }

    private WebAuthnRequestBean consumeStoredRequest(WebAuthnRequestType requestType, String requestKey) {
        Date now = new Date();
        WebAuthnRequestBean storedRequest = webAuthnRequestDao.getActiveRequestForUpdate(requestType, requestKey, now);
        if (storedRequest == null) {
            webAuthnRequestDao.deleteExpiredByTypeAndKey(requestType, requestKey, now);
            return null;
        }
        Integer deleted = webAuthnRequestDao.deleteByCode(storedRequest.getCode());
        if (deleted == null || deleted < 1) {
            return null;
        }
        return storedRequest;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void logOriginConfigurationWarning() {
        if (isBlank(relyingPartyHost) || isBlank(relyingPartyDomain)) {
            return;
        }
        String normalizedDomain = relyingPartyDomain.trim().toLowerCase(Locale.ROOT);
        String normalizedHost = relyingPartyHost.trim().toLowerCase(Locale.ROOT);
        if (!"localhost".equals(normalizedDomain) && !normalizedHost.startsWith("https://")) {
            LOG.warn("Passkey relying party host is not HTTPS. project.domain={}, project.host={}. " +
                    "Public passkey registration/login will fail when browser origin uses HTTPS.",
                    relyingPartyDomain, relyingPartyHost);
        }
    }

    private String resolvePasskeyFailureMessage(RegistrationFailedException exception) {
        String message = exception.getMessage();
        if (message != null && message.contains("Incorrect origin")) {
            return PASSKEY_ORIGIN_MISMATCH_MESSAGE + "，当前配置为 " + relyingPartyHost;
        }
        return "处理失败";
    }

    record AssertionVerificationResult(boolean success, String userName) {
    }

    record RegistrationVerificationResult(String userHandle, String credentialId, String publicKey) {
    }
}
