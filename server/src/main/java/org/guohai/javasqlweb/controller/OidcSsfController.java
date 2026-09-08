package org.guohai.javasqlweb.controller;

import org.guohai.javasqlweb.beans.*;
import org.guohai.javasqlweb.config.AdminPageRequired;
import org.guohai.javasqlweb.service.OidcSsfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * OIDC + SSF 管理控制器
 * @author guohai
 */
@RestController
@CrossOrigin
public class OidcSsfController {

    private static final Logger LOG = LoggerFactory.getLogger(OidcSsfController.class);

    @Autowired
    private OidcSsfService oidcSsfService;

    // ── OIDC ───────────────────────────────────────────

    /**
     * 获取 OIDC 授权 URL
     */
    @AdminPageRequired
    @GetMapping("/api/oidc/auth-url")
    @ResponseBody
    public Result<Map<String, String>> getAuthUrl(HttpServletRequest request) {
        try {
            Map<String, String> data = oidcSsfService.buildAuthorizationUrl(request);
            return new Result<>(true, "OK", data);
        } catch (Exception e) {
            return new Result<>(false, "OIDC is not configured", null);
        }
    }

    /**
     * OIDC 回调 — 不需要登录验证 (来自 OIDC Provider 的重定向)
     */
    @GetMapping("/api/oidc/callback")
    public void callback(@RequestParam("code") String code,
                         @RequestParam("state") String state,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        Result<OidcTokenInfo> result = oidcSsfService.exchangeCodeForTokens(code, state, request);
        if (result.getStatus()) {
            // 授权成功，重定向回 Admin 页面
            response.sendRedirect("/admin");
        } else {
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write(
                    "<html><body><h2>OIDC Authorization Failed</h2>"
                            + "<p>" + result.getMessage() + "</p>"
                            + "<a href=\"/admin\">Return to Admin</a></body></html>"
            );
        }
    }

    /**
     * 获取 OIDC 连接状态
     */
    @AdminPageRequired
    @GetMapping("/api/oidc/status")
    @ResponseBody
    public Result<OidcTokenInfo> getStatus() {
        return oidcSsfService.getStatus();
    }

    /**
     * 获取 UserInfo
     */
    @AdminPageRequired
    @GetMapping("/api/oidc/userinfo")
    @ResponseBody
    public Result<OidcUserInfo> getUserInfo() {
        return oidcSsfService.getUserInfo();
    }

    /**
     * 刷新令牌
     */
    @AdminPageRequired
    @PostMapping("/api/oidc/refresh")
    @ResponseBody
    public Result<OidcTokenInfo> refreshTokens() {
        return oidcSsfService.refreshTokens();
    }

    /**
     * 断开 OIDC 连接
     */
    @AdminPageRequired
    @PostMapping("/api/oidc/disconnect")
    @ResponseBody
    public Result<String> disconnect() {
        return oidcSsfService.disconnect();
    }

    // ── SSF ────────────────────────────────────────────

    /**
     * 获取 SSF stream 配置
     */
    @AdminPageRequired
    @GetMapping("/api/ssf/stream")
    @ResponseBody
    public Result<SsfStreamConfig> getSsfStream() {
        return oidcSsfService.getSsfStream();
    }

    /**
     * 创建 SSF stream
     */
    @AdminPageRequired
    @PostMapping("/api/ssf/stream")
    @ResponseBody
    public Result<SsfStreamConfig> createSsfStream(@RequestBody Map<String, Object> body) {
        String endpointUrl = (String) body.get("endpointUrl");
        @SuppressWarnings("unchecked")
        List<String> eventsRequested = (List<String>) body.get("eventsRequested");
        return oidcSsfService.createSsfStream(endpointUrl, eventsRequested);
    }

    /**
     * 更新 SSF stream
     */
    @AdminPageRequired
    @PutMapping("/api/ssf/stream")
    @ResponseBody
    public Result<SsfStreamConfig> updateSsfStream(@RequestBody Map<String, Object> body) {
        String status = (String) body.get("status");
        @SuppressWarnings("unchecked")
        List<String> eventsRequested = (List<String>) body.get("eventsRequested");
        return oidcSsfService.updateSsfStream(status, eventsRequested);
    }

    /**
     * 删除 SSF stream
     */
    @AdminPageRequired
    @DeleteMapping("/api/ssf/stream")
    @ResponseBody
    public Result<String> deleteSsfStream() {
        return oidcSsfService.deleteSsfStream();
    }

    /**
     * 请求验证事件
     */
    @AdminPageRequired
    @PostMapping("/api/ssf/verify")
    @ResponseBody
    public Result<String> requestVerification() {
        return oidcSsfService.requestVerification();
    }

    /**
     * SSF Push 事件接收端点 — 不需要登录验证 (来自 SSF Transmitter)
     */
    @PostMapping("/api/ssf/events")
    @ResponseBody
    public Map<String, Object> receiveSsfEvent(@RequestBody String setToken) {
        LOG.info("Received SSF push event");
        Result<String> result = oidcSsfService.receiveSsfEvent(setToken);
        if (result.getStatus()) {
            return Map.of();
        }
        return Map.of("err", result.getMessage());
    }

    /**
     * 获取事件日志
     */
    @AdminPageRequired
    @GetMapping("/api/ssf/events/log")
    @ResponseBody
    public Result<List<SsfEvent>> getEventLog() {
        return new Result<>(true, "OK", oidcSsfService.getEventLog());
    }

    // ── OIDC Config ─────────────────────────────────────

    /**
     * 获取 OIDC 配置（secret 脱敏）
     */
    @AdminPageRequired
    @GetMapping("/api/oidc/config")
    @ResponseBody
    public Result<OidcConfigBean> getOidcConfig(HttpServletRequest request) {
        return oidcSsfService.getOidcConfig(request);
    }

    /**
     * 保存 OIDC 配置
     */
    @AdminPageRequired
    @PostMapping("/api/oidc/config")
    @ResponseBody
    public Result<OidcConfigBean> saveOidcConfig(@RequestBody OidcConfigBean config) {
        return oidcSsfService.saveOidcConfig(config);
    }

    /**
     * 删除 OIDC 配置
     */
    @AdminPageRequired
    @DeleteMapping("/api/oidc/config")
    @ResponseBody
    public Result<String> deleteOidcConfig() {
        return oidcSsfService.deleteOidcConfig();
    }

    /**
     * 测试 OIDC Provider 连通性
     */
    @AdminPageRequired
    @PostMapping("/api/oidc/config/test")
    @ResponseBody
    public Result<Map<String, Object>> testOidcConnection(@RequestBody Map<String, String> body) {
        return oidcSsfService.testOidcConnection(body.get("openidConfigurationUrl"), body.get("ssfConfigurationUrl"));
    }

    // ── OIDC Login ──────────────────────────────────────

    /**
     * 查询 OIDC 登录是否可用（公开端点）
     */
    @GetMapping("/api/oidc/login-enabled")
    @ResponseBody
    public Result<Map<String, Object>> isOidcLoginEnabled() {
        boolean enabled = oidcSsfService.isOidcLoginEnabled();
        return new Result<>(true, "OK", Map.of("enabled", enabled));
    }

    /**
     * 获取 OIDC 登录授权 URL（公开端点）
     */
    @GetMapping("/api/oidc/login-url")
    @ResponseBody
    public Result<Map<String, String>> getLoginUrl(HttpServletRequest request) {
        if (!oidcSsfService.isOidcLoginEnabled()) {
            return new Result<>(false, "OIDC login is not enabled", null);
        }
        try {
            Map<String, String> data = oidcSsfService.buildLoginAuthorizationUrl(request);
            return new Result<>(true, "OK", data);
        } catch (Exception e) {
            return new Result<>(false, "OIDC login is not enabled", null);
        }
    }

    /**
     * OIDC 登录回调（公开端点）
     * 认证成功后重定向到前端并附带 token
     */
    @GetMapping("/api/oidc/login/callback")
    public void loginCallback(@RequestParam(value = "code", required = false) String code,
                              @RequestParam(value = "state", required = false) String state,
                              @RequestParam(value = "error", required = false) String error,
                              @RequestParam(value = "error_description", required = false) String errorDescription,
                              HttpServletRequest request,
                              HttpServletResponse response) throws IOException {
        if (error != null && !error.isBlank()) {
            String friendlyMessage = buildOidcLoginErrorMessage(error, errorDescription);
            String redirectUrl = "/login?oidc_error=" + URLEncoder.encode(error, StandardCharsets.UTF_8)
                    + "&oidc_error_description=" + URLEncoder.encode(friendlyMessage, StandardCharsets.UTF_8);
            response.sendRedirect(redirectUrl);
            return;
        }
        Result<UserBean> result = oidcSsfService.handleLoginCallback(code, state, request);
        if (result.getStatus() && result.getData() != null) {
            UserBean user = result.getData();
            String token = user.getToken();
            String authStatus = user.getAuthStatus() != null ? user.getAuthStatus().name() : "";

            StringBuilder redirectUrl = new StringBuilder("/login?oidc_token=").append(token)
                    .append("&auth_status=").append(authStatus);

            // BINDING 状态需要传递 authSecret 给前端显示二维码
            if ("BINDING".equals(authStatus) && user.getAuthSecret() != null) {
                redirectUrl.append("&auth_secret=").append(user.getAuthSecret())
                           .append("&user_name=").append(
                                   java.net.URLEncoder.encode(user.getUserName(), java.nio.charset.StandardCharsets.UTF_8));
            }

            response.sendRedirect(redirectUrl.toString());
        } else {
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write(
                    "<html><body><h2>OIDC 登录失败</h2>"
                            + "<p>" + result.getMessage() + "</p>"
                            + "<a href=\"/login\">返回登录页</a></body></html>"
            );
        }
    }

    private String buildOidcLoginErrorMessage(String error, String errorDescription) {
        if (errorDescription != null && !errorDescription.isBlank()) {
            return errorDescription;
        }
        if ("access_denied".equals(error)) {
            return "你已取消本次 OIDC 授权登录";
        }
        return "OIDC 授权失败，请稍后重试";
    }
}
