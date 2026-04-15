package org.guohai.javasqlweb.service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.guohai.javasqlweb.beans.OtpAuthStatus;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.beans.UserLoginStatus;
import org.guohai.javasqlweb.dao.UserManageDao;
import org.guohai.javasqlweb.util.AccessTokenUtils;
import org.guohai.javasqlweb.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

/**
 * 用户操作类
 * @author guohai
 */
@Service
public class UserServiceImpl implements UserService {

    private static final Logger LOG = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final String ADMIN = "admin";
    private static final String NOT_LOGGED_IN = "not logged in";
    private static final String ACCESS_TOKEN_INVALID = "access token invalid";
    private static final String ACCESS_TOKEN_EXPIRED = "access token expired";
    private static final String ACCESS_TOKEN_REQUIRED_OTP = "需先绑定OTP后才能管理访问令牌";
    private static final Long FW = Long.valueOf(1000 * 60 * 5);

    /**
     * 管理DAO
     */
    @Autowired
    UserManageDao userDao;

    @Value("${project.signkey}")
    private String signKey;

    /**
     * 登录方法
     *
     * @param name
     * @param pass
     * @return
     */
    @Override
    public Result<UserBean> login(String name, String pass) {
        UserBean user = userDao.checkUserNamePass(name, pass);
        if (user == null) {
            return new Result<>(false, "登录失败", null);
        }
        user.setToken(UUID.randomUUID().toString());
        if (OtpAuthStatus.UNBIND == user.getAuthStatus() || OtpAuthStatus.BINDING == user.getAuthStatus()) {
            GoogleAuthenticator gAuth = new GoogleAuthenticator();
            final GoogleAuthenticatorKey key = gAuth.createCredentials();
            user.setAuthSecret(key.getKey());
            userDao.setUserSecret(user.getAuthSecret(), user.getToken(), user.getUserName());
            return new Result<>(true, "success", user);
        }
        if (userDao.setUserToken(name, user.getToken())) {
            return new Result<>(true, "success", user);
        }
        return new Result<>(false, "网络异常请重试", null);
    }

    /**
     * 注销方法
     *
     * @param token
     * @return
     */
    @Override
    public Result<String> logout(String token) {
        userDao.logoutUser(token);
        return new Result<>(true, "注销成功", "success");
    }

    /**
     * 检查登录状态
     *
     * @param token
     * @return
     */
    @Override
    public Result<UserBean> checkLoginStatus(String token) {
        UserBean user = userDao.getUserByToken(token);
        if (user == null || UserLoginStatus.LOGGED != user.getLoginStatus()) {
            return new Result<>(false, "未登录", null);
        }
        return new Result<>(true, "success", sanitizeLoginUser(user));
    }

    /**
     * 校验业务查询接口认证
     * @param token 短期登录态
     * @param authorizationHeader Bearer头
     * @return 认证结果
     */
    @Override
    public Result<UserBean> checkApiAccess(String token, String authorizationHeader) {
        if (authorizationHeader != null && !authorizationHeader.trim().isEmpty()) {
            final String prefix = "Bearer ";
            if (!authorizationHeader.startsWith(prefix)) {
                return new Result<>(false, ACCESS_TOKEN_INVALID, null);
            }
            String accessToken = authorizationHeader.substring(prefix.length()).trim();
            if (accessToken.isEmpty()) {
                return new Result<>(false, ACCESS_TOKEN_INVALID, null);
            }

            UserBean user = userDao.getUserByAccessToken(accessToken);
            if (user == null) {
                return new Result<>(false, ACCESS_TOKEN_INVALID, null);
            }
            if (AccessTokenUtils.isExpired(user.getAccessTokenExpireTime())) {
                return new Result<>(false, ACCESS_TOKEN_EXPIRED, null);
            }
            return new Result<>(true, "success", sanitizeLoginUser(user));
        }

        Result<UserBean> loginResult = checkLoginStatus(token);
        if (!loginResult.getStatus()) {
            return new Result<>(false, NOT_LOGGED_IN, null);
        }
        return new Result<>(true, "success", loginResult.getData());
    }

    /**
     * 校验后台管理接口认证
     * @param token 短期登录态
     * @return 认证结果
     */
    @Override
    public Result<UserBean> checkAdminAccess(String token) {
        Result<UserBean> loginResult = checkLoginStatus(token);
        if (!loginResult.getStatus()) {
            return new Result<>(false, NOT_LOGGED_IN, null);
        }
        if (!ADMIN.equals(loginResult.getData().getUserName())) {
            return new Result<>(false, NOT_LOGGED_IN, null);
        }
        return loginResult;
    }

    /**
     * 绑定OTP
     *
     * @param token   用户令牌
     * @param otpPass 一次密码
     * @return
     */
    @Override
    public Result<String> bindOtp(String token, String otpPass) {
        UserBean user = userDao.getUserByToken(token);
        if (user == null) {
            return new Result<>(false, "用户还未登录", "token_error");
        }
        if ("".equals(user.getAuthSecret()) || OtpAuthStatus.BINDING != user.getAuthStatus()
                || UserLoginStatus.LOGGING != user.getLoginStatus()) {
            return new Result<>(false, "状态错误，跳回登录", "status_error");
        }
        GoogleAuthenticator gAuth = new GoogleAuthenticator();
        Boolean authResult = gAuth.authorize(user.getAuthSecret(), Integer.parseInt(otpPass));
        if (!authResult) {
            return new Result<>(false, "一次密码错误，请重新输入", "otp_pass_error");
        }
        userDao.setUserBindStatus(user.getUserName());
        return new Result<>(true, "成功", "success");
    }

    /**
     * 验证一次密钥
     *
     * @param token
     * @param otpPass
     * @return
     */
    @Override
    public Result<String> verifyOtp(String token, String otpPass) {
        UserBean user = userDao.getUserByToken(token);
        if (user == null) {
            return new Result<>(false, "还未登录", "token_error");
        }
        if ("".equals(user.getAuthSecret()) || OtpAuthStatus.BIND != user.getAuthStatus()
                || UserLoginStatus.LOGGING != user.getLoginStatus()) {
            return new Result<>(false, "状态异常", "status_error");
        }
        GoogleAuthenticator gAuth = new GoogleAuthenticator();
        Boolean authResult = gAuth.authorize(user.getAuthSecret(), Integer.parseInt(otpPass));
        if (!authResult) {
            return new Result<>(false, "动态码错误，请重新输入", "otp_pass_error");
        }
        userDao.setUserLoginSuccess(token);
        return new Result<>(true, "成功", "success");
    }

    /**
     * 注销用户
     *
     * @param token
     * @return
     */
    @Override
    public Result<String> logoutUser(String token) {
        userDao.logoutUser(token);
        return new Result<>(true, "成功", "");
    }

    /**
     * 通过链接创建用户
     *
     * @param userName
     * @param time
     * @param sign
     * @return
     */
    @Override
    public Result<UserBean> createUserByLink(String userName, String time, String sign) {
        Long currentTime = System.currentTimeMillis();
        Long requestTime = Long.parseLong(time) * 1000;
        LOG.info(String.format("当前时间: %d ,传入时间: %d", currentTime, requestTime));
        if (currentTime - FW > requestTime || currentTime + FW < requestTime) {
            return new Result<>(false, "时间范围异常", null);
        }
        if (!sign.equals(Utils.MD5(String.format("%s%s%s", userName, time, signKey)))) {
            return new Result<>(false, "签名没通过", null);
        }
        UserBean userBean = userDao.getUserByName(userName);

        if (userBean == null) {
            userDao.addNewUser(userName, userName);
            userBean = new UserBean();
            userBean.setUserName(userName);
            userBean.setAuthStatus(OtpAuthStatus.UNBIND);
        }
        LOG.info(String.format("数据库查询的用户状态，用户名%s,密保状态%s", userBean.getUserName(), userBean.getAuthStatus().toString()));
        userBean.setToken(UUID.randomUUID().toString());

        if (OtpAuthStatus.UNBIND == userBean.getAuthStatus() || OtpAuthStatus.BINDING == userBean.getAuthStatus()) {
            GoogleAuthenticator gAuth = new GoogleAuthenticator();
            final GoogleAuthenticatorKey key = gAuth.createCredentials();
            userBean.setAuthSecret(key.getKey());
            userDao.setUserSecret(userBean.getAuthSecret(), userBean.getToken(), userBean.getUserName());
            return new Result<>(true, "success", userBean);
        }
        if (userDao.setUserToken(userName, userBean.getToken())) {
            return new Result<>(true, "success", userBean);
        }
        return null;
    }

    /**
     * 获取访问令牌信息
     * @param token 短期登录态
     * @return 令牌信息
     */
    @Override
    public Result<UserBean> getAccessTokenInfo(String token) {
        Result<UserBean> loginResult = getCurrentLoggedInUser(token);
        if (!loginResult.getStatus()) {
            return loginResult;
        }
        return new Result<>(true, "success", buildAccessTokenResponse(loginResult.getData(), false));
    }

    /**
     * 首次生成访问令牌
     * @param token 短期登录态
     * @return 令牌信息
     */
    @Override
    public Result<UserBean> createAccessToken(String token) {
        Result<UserBean> loginResult = getCurrentLoggedInUser(token);
        if (!loginResult.getStatus()) {
            return loginResult;
        }
        UserBean user = loginResult.getData();
        if (OtpAuthStatus.BIND != user.getAuthStatus()) {
            return new Result<>(false, ACCESS_TOKEN_REQUIRED_OTP, null);
        }
        if (AccessTokenUtils.hasAccessToken(user)) {
            return new Result<>(false, "访问令牌已存在，请直接续期或重置", null);
        }

        String accessToken = AccessTokenUtils.generateAccessToken();
        Date expireTime = AccessTokenUtils.buildExpireTime();
        if (!Boolean.TRUE.equals(userDao.setAccessToken(user.getCode(), accessToken, expireTime))) {
            return new Result<>(false, "访问令牌生成失败", null);
        }

        user.setAccessToken(accessToken);
        user.setAccessTokenExpireTime(expireTime);
        return new Result<>(true, "访问令牌申请成功", buildAccessTokenResponse(user, true));
    }

    /**
     * 续期访问令牌
     * @param token 短期登录态
     * @return 令牌信息
     */
    @Override
    public Result<UserBean> renewAccessToken(String token) {
        Result<UserBean> loginResult = getCurrentLoggedInUser(token);
        if (!loginResult.getStatus()) {
            return loginResult;
        }
        UserBean user = loginResult.getData();
        if (OtpAuthStatus.BIND != user.getAuthStatus()) {
            return new Result<>(false, ACCESS_TOKEN_REQUIRED_OTP, null);
        }
        if (!AccessTokenUtils.hasAccessToken(user)) {
            return new Result<>(false, "访问令牌不存在，请先申请", null);
        }

        Date expireTime = AccessTokenUtils.buildExpireTime();
        if (!Boolean.TRUE.equals(userDao.renewAccessToken(user.getCode(), expireTime))) {
            return new Result<>(false, "访问令牌续期失败", null);
        }
        user.setAccessTokenExpireTime(expireTime);
        return new Result<>(true, "访问令牌续期成功", buildAccessTokenResponse(user, false));
    }

    /**
     * 重置访问令牌
     * @param token 短期登录态
     * @return 令牌信息
     */
    @Override
    public Result<UserBean> resetAccessToken(String token) {
        Result<UserBean> loginResult = getCurrentLoggedInUser(token);
        if (!loginResult.getStatus()) {
            return loginResult;
        }
        UserBean user = loginResult.getData();
        if (OtpAuthStatus.BIND != user.getAuthStatus()) {
            return new Result<>(false, ACCESS_TOKEN_REQUIRED_OTP, null);
        }
        if (!AccessTokenUtils.hasAccessToken(user)) {
            return new Result<>(false, "访问令牌不存在，请先申请", null);
        }

        String accessToken = AccessTokenUtils.generateAccessToken();
        Date expireTime = AccessTokenUtils.buildExpireTime();
        if (!Boolean.TRUE.equals(userDao.setAccessToken(user.getCode(), accessToken, expireTime))) {
            return new Result<>(false, "访问令牌重置失败", null);
        }

        user.setAccessToken(accessToken);
        user.setAccessTokenExpireTime(expireTime);
        return new Result<>(true, "访问令牌重置成功", buildAccessTokenResponse(user, true));
    }

    private Result<UserBean> getCurrentLoggedInUser(String token) {
        UserBean user = userDao.getUserByToken(token);
        if (user == null || UserLoginStatus.LOGGED != user.getLoginStatus()) {
            return new Result<>(false, "未登录", null);
        }
        return new Result<>(true, "success", user);
    }

    private UserBean sanitizeLoginUser(UserBean user) {
        user.setAuthSecret("");
        user.setAccessToken(null);
        user.setMaskedAccessToken(null);
        user.setAccessTokenFullVisible(false);
        user.setCanCreateAccessToken(null);
        user.setCanRenewAccessToken(null);
        user.setCanResetAccessToken(null);
        return user;
    }

    private UserBean buildAccessTokenResponse(UserBean user, boolean includeFullToken) {
        user.setAuthSecret("");
        user.setHasAccessToken(AccessTokenUtils.hasAccessToken(user));
        user.setAccessTokenStatus(AccessTokenUtils.resolveStatus(user));
        user.setMaskedAccessToken(AccessTokenUtils.maskAccessToken(user.getAccessToken()));
        user.setAccessTokenFullVisible(includeFullToken && Boolean.TRUE.equals(user.getHasAccessToken()));
        user.setCanCreateAccessToken(OtpAuthStatus.BIND == user.getAuthStatus() && !Boolean.TRUE.equals(user.getHasAccessToken()));
        user.setCanRenewAccessToken(OtpAuthStatus.BIND == user.getAuthStatus() && Boolean.TRUE.equals(user.getHasAccessToken()));
        user.setCanResetAccessToken(OtpAuthStatus.BIND == user.getAuthStatus() && Boolean.TRUE.equals(user.getHasAccessToken()));
        if (!includeFullToken) {
            user.setAccessToken(null);
        }
        return user;
    }
}
