package org.guohai.javasqlweb.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.DatabaseNameBean;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.beans.VannaContextResponse;
import org.guohai.javasqlweb.beans.VannaServerWarmupItem;
import org.guohai.javasqlweb.config.AuthenticationInterceptor;
import org.guohai.javasqlweb.service.BaseDataService;
import org.guohai.javasqlweb.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 供 Vanna 容器使用的内部上下文接口。
 */
@RestController
@RequestMapping("/internal/vanna")
public class InternalVannaController {

    @Autowired
    private BaseDataService baseDataService;

    @Autowired
    private UserService userService;

    @Value("${project.vanna-internal-token:}")
    private String vannaInternalToken;

    private boolean isValidInternalToken(String internalToken) {
        return vannaInternalToken != null
                && !vannaInternalToken.isBlank()
                && vannaInternalToken.equals(internalToken);
    }

    /**
     * 返回指定用户在指定 server/db 范围内可见的问数上下文。
     */
    @GetMapping("/context/{serverCode}/{dbName}")
    public Result<VannaContextResponse> getContext(@PathVariable("serverCode") String serverCode,
                                                   @PathVariable("dbName") String dbName,
                                                   @RequestHeader(value = "X-Vanna-Internal-Token", required = false) String internalToken,
                                                   @RequestHeader(value = "X-Vanna-Warmup", required = false) String warmupHeader,
                                                   @RequestHeader(value = "User-Token", required = false) String userToken,
                                                   @RequestHeader(value = "Authorization", required = false) String authorization,
                                                   HttpServletRequest request) {
        if (!isValidInternalToken(internalToken)) {
            return new Result<>(false, "invalid internal token", null);
        }
        if ("true".equalsIgnoreCase(warmupHeader)) {
            return baseDataService.getVannaWarmupContext(Integer.parseInt(serverCode), dbName);
        }
        Result<UserBean> userResult = userService.checkApiAccess(userToken, authorization);
        if (!userResult.getStatus() || userResult.getData() == null) {
            return new Result<>(false, userResult.getMessage(), null);
        }
        UserBean user = userResult.getData();
        request.setAttribute(AuthenticationInterceptor.AUTHENTICATED_USER_ATTR, user);
        return baseDataService.getVannaContext(Integer.parseInt(serverCode), dbName, user);
    }

    /**
     * 返回预热任务需要遍历的所有服务器。
     */
    @GetMapping("/servers")
    public Result<List<VannaServerWarmupItem>> getWarmupServers(
            @RequestHeader(value = "X-Vanna-Internal-Token", required = false) String internalToken) {
        if (!isValidInternalToken(internalToken)) {
            return new Result<>(false, "invalid internal token", null);
        }
        return baseDataService.getVannaWarmupServers();
    }

    /**
     * 返回指定服务器下所有可预热数据库。
     */
    @GetMapping("/databases/{serverCode}")
    public Result<List<DatabaseNameBean>> getWarmupDatabases(
            @PathVariable("serverCode") String serverCode,
            @RequestHeader(value = "X-Vanna-Internal-Token", required = false) String internalToken) {
        if (!isValidInternalToken(internalToken)) {
            return new Result<>(false, "invalid internal token", null);
        }
        return baseDataService.getVannaWarmupDatabases(Integer.parseInt(serverCode));
    }
}
