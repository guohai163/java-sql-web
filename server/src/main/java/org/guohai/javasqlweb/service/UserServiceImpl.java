package org.guohai.javasqlweb.service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.guohai.javasqlweb.beans.OtpAuthStatus;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.SecurityTaskInfo;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.beans.UserLoginStatus;
import org.guohai.javasqlweb.beans.AccountStatus;
import org.guohai.javasqlweb.dao.UserManageDao;
import org.guohai.javasqlweb.util.AccessTokenUtils;
import org.guohai.javasqlweb.util.PasswordUtils;
import org.guohai.javasqlweb.util.RateLimitUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final String TOO_MANY_REQUESTS = "请求过于频繁，请稍后再试";
    private static final long RATE_LIMIT_WINDOW_MS = 5 * 60 * 1000L;
    private static final String ACCOUNT_PENDING_ACTIVATION_MESSAGE = "账号未激活，请使用管理员发送的激活链接完成初始化密码和OTP绑定";
    private static final String ACCOUNT_PENDING_PASSWORD_RESET_MESSAGE = "账号正在等待密码重置，请使用管理员发送的重置链接完成密码设置";
    private static final String ACCOUNT_PENDING_OTP_RESET_MESSAGE = "账号正在等待OTP重绑，请使用管理员发送的链接完成OTP绑定";

    /**
     * 管理DAO
     */
    @Autowired
    UserManageDao userDao;

    @Autowired
    UserSecurityTaskService userSecurityTaskService;

    /**
     * 登录方法
     *
     * @param name
     * @param pass
     * @return
     */
    @Override
    public Result<UserBean> login(String name, String pass) {
        if (!RateLimitUtils.tryAcquire("login", name, 10, RATE_LIMIT_WINDOW_MS)) {
            return new Result<>(false, TOO_MANY_REQUESTS, null);
        }
        UserBean user = userDao.getUserLoginDataByName(name);
        if (user == null) {
            return new Result<>(false, "登录失败", null);
        }
        Result<UserBean> accountStatusResult = ensureAccountReadyForLogin(user);
        if (!accountStatusResult.getStatus()) {
            return accountStatusResult;
        }
        if (!isPasswordMatched(user, pass)) {
            return new Result<>(false, "登录失败", null);
        }
        upgradeLegacyPasswordIfNeeded(user, pass);
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

            UserBean user = userDao.getUserByAccessTokenHash(AccessTokenUtils.hashAccessToken(accessToken));
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
        if (!RateLimitUtils.tryAcquire("bindOtp", token, 10, RATE_LIMIT_WINDOW_MS)) {
            return new Result<>(false, TOO_MANY_REQUESTS, "rate_limit");
        }
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
        if (!RateLimitUtils.tryAcquire("verifyOtp", token, 10, RATE_LIMIT_WINDOW_MS)) {
            return new Result<>(false, TOO_MANY_REQUESTS, "rate_limit");
        }
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
     * 已登录用户修改密码
     * @param token 登录态
     * @param newPass 新密码
     * @return 结果
     */
    @Override
    public Result<String> changePassword(String token, String newPass) {
        Result<UserBean> loginResult = getCurrentLoggedInUser(token);
        if (!loginResult.getStatus()) {
            return new Result<>(false, loginResult.getMessage(), null);
        }
        String passwordValidationMessage = PasswordUtils.validateComplexity(newPass);
        if (passwordValidationMessage != null) {
            return new Result<>(false, passwordValidationMessage, null);
        }
        userDao.changeUserPasswordByCode(loginResult.getData().getCode(), PasswordUtils.encode(newPass));
        return new Result<>(true, "密码修改成功", "success");
    }

    /**
     * 查询安全任务信息
     * @param uuid 安全任务UUID
     * @return 任务信息
     */
    @Override
    public Result<SecurityTaskInfo> getSecurityTaskInfo(String uuid) {
        return userSecurityTaskService.getTaskInfo(uuid);
    }

    /**
     * 提交安全任务密码
     * @param uuid 安全任务UUID
     * @param newPass 新密码
     * @return 任务信息
     */
    @Override
    public Result<SecurityTaskInfo> submitSecurityTaskPassword(String uuid, String newPass) {
        return userSecurityTaskService.submitPassword(uuid, newPass);
    }

    /**
     * 创建安全任务OTP会话
     * @param uuid 安全任务UUID
     * @return 任务信息
     */
    @Override
    public Result<SecurityTaskInfo> createSecurityTaskOtpSession(String uuid) {
        return userSecurityTaskService.createOtpSession(uuid);
    }

    /**
     * 安全任务绑定OTP
     * @param uuid 安全任务UUID
     * @param token OTP会话token
     * @param otpPass 动态码
     * @return 结果
     */
    @Override
    public Result<String> bindSecurityTaskOtp(String uuid, String token, String otpPass) {
        return userSecurityTaskService.bindOtp(uuid, token, otpPass);
    }

    /**
     * 获取访问令牌信息
     * @param token 短期登录态
     * @return 令牌信息
     */
    @Override
    public Result<UserBean> getAccessTokenInfo(String token) {
        if (!RateLimitUtils.tryAcquire("accessTokenInfo", token, 30, RATE_LIMIT_WINDOW_MS)) {
            return new Result<>(false, TOO_MANY_REQUESTS, null);
        }
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
        if (!RateLimitUtils.tryAcquire("createAccessToken", token, 10, RATE_LIMIT_WINDOW_MS)) {
            return new Result<>(false, TOO_MANY_REQUESTS, null);
        }
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
        String accessTokenHash = AccessTokenUtils.hashAccessToken(accessToken);
        if (!Boolean.TRUE.equals(userDao.setAccessTokenHash(user.getCode(), accessTokenHash, expireTime))) {
            return new Result<>(false, "访问令牌生成失败", null);
        }

        user.setAccessToken(accessToken);
        user.setAccessTokenHash(accessTokenHash);
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
        if (!RateLimitUtils.tryAcquire("renewAccessToken", token, 10, RATE_LIMIT_WINDOW_MS)) {
            return new Result<>(false, TOO_MANY_REQUESTS, null);
        }
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
        if (!RateLimitUtils.tryAcquire("resetAccessToken", token, 10, RATE_LIMIT_WINDOW_MS)) {
            return new Result<>(false, TOO_MANY_REQUESTS, null);
        }
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
        String accessTokenHash = AccessTokenUtils.hashAccessToken(accessToken);
        if (!Boolean.TRUE.equals(userDao.setAccessTokenHash(user.getCode(), accessTokenHash, expireTime))) {
            return new Result<>(false, "访问令牌重置失败", null);
        }

        user.setAccessToken(accessToken);
        user.setAccessTokenHash(accessTokenHash);
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

    private boolean isPasswordMatched(UserBean user, String rawPassword) {
        if (PasswordUtils.isBcryptHash(user.getPassWord())) {
            return PasswordUtils.matches(rawPassword, user.getPassWord());
        }
        return PasswordUtils.legacyHash(rawPassword).equals(user.getPassWord());
    }

    private void upgradeLegacyPasswordIfNeeded(UserBean user, String rawPassword) {
        if (!PasswordUtils.isBcryptHash(user.getPassWord())) {
            userDao.changeUserPasswordByCode(user.getCode(), PasswordUtils.encode(rawPassword));
        }
    }

    private Result<UserBean> ensureAccountReadyForLogin(UserBean user) {
        AccountStatus accountStatus = user.getAccountStatus();
        if (accountStatus == null || accountStatus == AccountStatus.ACTIVE) {
            return new Result<>(true, "success", user);
        }
        if (accountStatus == AccountStatus.PENDING_ACTIVATION) {
            return new Result<>(false, ACCOUNT_PENDING_ACTIVATION_MESSAGE, null);
        }
        if (accountStatus == AccountStatus.PENDING_PASSWORD_RESET) {
            return new Result<>(false, ACCOUNT_PENDING_PASSWORD_RESET_MESSAGE, null);
        }
        if (accountStatus == AccountStatus.PENDING_OTP_RESET) {
            return new Result<>(false, ACCOUNT_PENDING_OTP_RESET_MESSAGE, null);
        }
        return new Result<>(false, "登录失败", null);
    }
}
