package org.guohai.javasqlweb.config;

import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
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
    @Autowired
    private UserService userService;

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
        LoginRequired classAnnotation = bean.getClass().getAnnotation(LoginRequired.class);
        LoginRequired methodAnnotation = method.getAnnotation(LoginRequired.class);
        if(classAnnotation != null || methodAnnotation != null){
            // 需要检查的
            String token = request.getHeader("User-Token");
            if(userService.checkLoginStatus(token).getStatus()){
                return true;
            }
            response.setCharacterEncoding("UTF-8");
            response.setStatus(400);
            response.getWriter().write(String.valueOf(new Result<String>(false,"check login ",null)));
            return false;
        }
        return true;
    }
}
