package org.guohai.javasqlweb.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.beans.VannaContextResponse;
import org.guohai.javasqlweb.config.AuthenticationInterceptor;
import org.guohai.javasqlweb.service.BaseDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供 Vanna 容器使用的内部上下文接口。
 */
@RestController
@RequestMapping("/internal/vanna")
public class InternalVannaController {

    @Autowired
    private BaseDataService baseDataService;

    @Value("${project.vanna-internal-token:}")
    private String vannaInternalToken;

    /**
     * 返回指定用户在指定 server/db 范围内可见的问数上下文。
     */
    @GetMapping("/context/{serverCode}/{dbName}")
    public Result<VannaContextResponse> getContext(@PathVariable("serverCode") String serverCode,
                                                   @PathVariable("dbName") String dbName,
                                                   @RequestHeader(value = "X-Vanna-Internal-Token", required = false) String internalToken,
                                                   HttpServletRequest request) {
        if (vannaInternalToken == null || vannaInternalToken.isBlank() || !vannaInternalToken.equals(internalToken)) {
            return new Result<>(false, "invalid internal token", null);
        }
        UserBean user = (UserBean) request.getAttribute(AuthenticationInterceptor.AUTHENTICATED_USER_ATTR);
        if (user == null) {
            return new Result<>(false, "not logged in", null);
        }
        return baseDataService.getVannaContext(Integer.parseInt(serverCode), dbName, user);
    }
}
