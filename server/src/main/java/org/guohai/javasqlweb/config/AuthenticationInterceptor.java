package org.guohai.javasqlweb.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.controller.HomeController;
import org.guohai.javasqlweb.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 拦截器，对指定注解进行拦截
 * @author guohai
 */
@Component
public class AuthenticationInterceptor  implements HandlerInterceptor {

    public static final String AUTHENTICATED_USER_ATTR = "authenticatedUser";

    /**
     * 日志
     */
    private static final Logger LOG  = LoggerFactory.getLogger(HomeController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;
    /**
     *
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 如果不是映射到方法直接通过
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Method method = handlerMethod.getMethod();
        Object bean = handlerMethod.getBean();
        // 判断接口是否需要登录
        LoginRequired loginClassAnnotation = bean.getClass().getAnnotation(LoginRequired.class);
        LoginRequired loginMethodAnnotation = method.getAnnotation(LoginRequired.class);
        AdminPageRequired adminClassAnnotation = bean.getClass().getAnnotation(AdminPageRequired.class);
        AdminPageRequired adminMethodAnnotation = method.getAnnotation(AdminPageRequired.class);
        if(loginClassAnnotation != null || loginMethodAnnotation != null ||
                adminClassAnnotation != null || adminMethodAnnotation != null){
            Result<UserBean> userBeanResult = null;
            if(loginClassAnnotation != null || loginMethodAnnotation != null){
                boolean allowAccessToken = request.getRequestURI().startsWith("/database/");
                userBeanResult = userService.checkApiAccess(
                        request.getHeader("User-Token"),
                        allowAccessToken ? request.getHeader("Authorization") : null
                );
                if(userBeanResult.getStatus()){
                    request.setAttribute(AUTHENTICATED_USER_ATTR, userBeanResult.getData());
                    return true;
                }
            }
            if(adminClassAnnotation != null || adminMethodAnnotation != null){
                userBeanResult = userService.checkAdminAccess(request.getHeader("User-Token"));
                if(userBeanResult.getStatus()){
                    request.setAttribute(AUTHENTICATED_USER_ATTR, userBeanResult.getData());
                    return true;
                }
            }
            writeAuthError(response, userBeanResult == null ? "not logged in" : userBeanResult.getMessage());
            return false;
        }
        return true;
    }

    private void writeAuthError(HttpServletResponse response, String message) throws Exception {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write(objectMapper.writeValueAsString(new Result<String>(false, message, null)));
    }
}
