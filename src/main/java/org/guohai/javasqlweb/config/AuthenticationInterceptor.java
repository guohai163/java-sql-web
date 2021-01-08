package org.guohai.javasqlweb.config;

import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.controller.HomeController;
import org.guohai.javasqlweb.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

/**
 * 拦截器，对指定注解进行拦截
 * @author guohai
 */
@Component
public class AuthenticationInterceptor  implements HandlerInterceptor {

    /**
     * 日志
     */
    private static final Logger LOG  = LoggerFactory.getLogger(HomeController.class);

    @Autowired
    private UserService userService;

    private static String ADMIN = "admin";
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
            // 需要检查的
            String token = request.getHeader("User-Token");
            Result<UserBean> userBeanResult = userService.checkLoginStatus(token);

            if(loginClassAnnotation !=null || loginMethodAnnotation!=null){
                // 如果为登录检查走此流程,
                if(userBeanResult.getStatus()){
                    return true;
                }
            }
            if(adminClassAnnotation != null || adminMethodAnnotation != null){
                // 管理页面,检查
                if(userBeanResult.getStatus() && ADMIN.equals(userBeanResult.getData().getUserName())){
                    return true;
                }
            }
            response.setCharacterEncoding("UTF-8");
            response.setStatus(400);
            response.getWriter().write(String.valueOf(new Result<String>(false,"check login ",null)));
            return false;
        }
        return true;
    }
}
