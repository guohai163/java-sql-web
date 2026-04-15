package org.guohai.javasqlweb.service;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.guohai.javasqlweb.beans.*;
import org.guohai.javasqlweb.dao.UserManageDao;
import org.guohai.javasqlweb.dao.UserSecurityTaskDao;
import org.guohai.javasqlweb.util.EmailUtils;
import org.guohai.javasqlweb.util.PasswordUtils;
import org.guohai.javasqlweb.util.UserSecurityTaskUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * 用户安全任务服务实现
 */
@Service
public class UserSecurityTaskServiceImpl implements UserSecurityTaskService {

    private static final long TASK_EXPIRE_HOURS = 24L;
    private static final String INVALID_TASK_MESSAGE = "链接无效或已失效，请联系管理员重新生成";
    private static final String PASSWORD_RESET_BLOCK_MESSAGE = "该账号尚未激活，请重发激活链接";

    @Autowired
    private UserManageDao userDao;

    @Autowired
    private UserSecurityTaskDao taskDao;

    @Value("${project.host:http://localhost}")
    private String projectHost;

    @Override
    @Transactional
    public Result<LinkIssueResult> createActivationTask(String adminToken, String email) {
        if (!EmailUtils.isValid(email)) {
            return new Result<>(false, "邮箱格式不正确", null);
        }
        String normalizedEmail = EmailUtils.normalize(email);
        String userName = EmailUtils.extractUserName(normalizedEmail);
        if (userName == null || userName.trim().isEmpty()) {
            return new Result<>(false, "无法从邮箱中解析用户名", null);
        }
        if (userDao.getUserByEmail(normalizedEmail) != null) {
            return new Result<>(false, "邮箱已存在", null);
        }
        if (userDao.getUserByName(userName) != null) {
            return new Result<>(false, "用户名已存在，请更换邮箱前缀", null);
        }

        userDao.addNewUser(userName, normalizedEmail,
                PasswordUtils.encode(PasswordUtils.randomTemporaryPassword()),
                AccountStatus.PENDING_ACTIVATION.name());
        UserBean user = userDao.getUserByName(userName);
        if (user == null) {
            return new Result<>(false, "用户创建失败", null);
        }
        return new Result<>(true, "用户创建成功",
                issueTask(user, UserSecurityTaskType.ACTIVATE, resolveCreator(adminToken)));
    }

    @Override
    @Transactional
    public Result<LinkIssueResult> reissueActivationTask(String adminToken, String userName) {
        UserBean user = userDao.getUserByName(userName);
        if (user == null) {
            return new Result<>(false, "用户不存在", null);
        }
        if (user.getAccountStatus() != AccountStatus.PENDING_ACTIVATION) {
            return new Result<>(false, "当前用户不处于待激活状态", null);
        }
        return new Result<>(true, "激活链接已重新生成",
                issueTask(user, UserSecurityTaskType.ACTIVATE, resolveCreator(adminToken)));
    }

    @Override
    @Transactional
    public Result<LinkIssueResult> createPasswordResetTask(String adminToken, String userName) {
        UserBean user = userDao.getUserByName(userName);
        if (user == null) {
            return new Result<>(false, "用户不存在", null);
        }
        if (user.getAccountStatus() == AccountStatus.PENDING_ACTIVATION) {
            return new Result<>(false, PASSWORD_RESET_BLOCK_MESSAGE, null);
        }

        userDao.changePasswordAndRevokeLoginByCode(user.getCode(),
                PasswordUtils.encode(PasswordUtils.randomTemporaryPassword()),
                AccountStatus.PENDING_PASSWORD_RESET.name());
        user.setAccountStatus(AccountStatus.PENDING_PASSWORD_RESET);
        return new Result<>(true, "密码重置链接已生成",
                issueTask(user, UserSecurityTaskType.RESET_PASSWORD, resolveCreator(adminToken)));
    }

    @Override
    @Transactional
    public Result<LinkIssueResult> createOtpResetTask(String adminToken, String userName) {
        UserBean user = userDao.getUserByName(userName);
        if (user == null) {
            return new Result<>(false, "用户不存在", null);
        }
        if (user.getAccountStatus() == AccountStatus.PENDING_ACTIVATION) {
            return new Result<>(false, PASSWORD_RESET_BLOCK_MESSAGE, null);
        }

        userDao.resetUserOtpByCode(user.getCode(), AccountStatus.PENDING_OTP_RESET.name());
        user.setAccountStatus(AccountStatus.PENDING_OTP_RESET);
        user.setAuthStatus(OtpAuthStatus.UNBIND);
        return new Result<>(true, "OTP重绑链接已生成",
                issueTask(user, UserSecurityTaskType.RESET_OTP, resolveCreator(adminToken)));
    }

    @Override
    public Result<SecurityTaskInfo> getTaskInfo(String uuid) {
        Result<UserSecurityTaskBean> taskResult = loadActiveTask(uuid, null, null);
        if (!taskResult.getStatus()) {
            return new Result<>(false, taskResult.getMessage(), null);
        }
        return new Result<>(true, "success", toTaskInfo(taskResult.getData()));
    }

    @Override
    @Transactional
    public Result<SecurityTaskInfo> submitPassword(String uuid, String password) {
        Result<UserSecurityTaskBean> taskResult = loadActiveTask(uuid,
                EnumSet.of(UserSecurityTaskType.ACTIVATE, UserSecurityTaskType.RESET_PASSWORD),
                EnumSet.of(UserSecurityTaskStatus.PENDING_PASSWORD));
        if (!taskResult.getStatus()) {
            return new Result<>(false, taskResult.getMessage(), null);
        }

        String passwordValidationMessage = PasswordUtils.validateComplexity(password);
        if (passwordValidationMessage != null) {
            return new Result<>(false, passwordValidationMessage, null);
        }

        UserSecurityTaskBean task = taskResult.getData();
        userDao.changeUserPasswordByCode(task.getUserCode(), PasswordUtils.encode(password));

        if (task.getTaskType() == UserSecurityTaskType.ACTIVATE) {
            UserBean user = userDao.getUserByCode(task.getUserCode());
            SecurityTaskInfo taskInfo = beginOtpBinding(user, task);
            taskDao.updateTaskStatus(task.getCode(), UserSecurityTaskStatus.PENDING_OTP.name());
            taskInfo.setTaskStatus(UserSecurityTaskStatus.PENDING_OTP);
            return new Result<>(true, "密码设置成功，请继续绑定OTP", taskInfo);
        }

        userDao.updateAccountStatus(task.getUserCode(), AccountStatus.ACTIVE.name());
        taskDao.markUsed(task.getCode(), new Date());
        SecurityTaskInfo taskInfo = toTaskInfo(task);
        taskInfo.setTaskStatus(UserSecurityTaskStatus.USED);
        taskInfo.setToken(null);
        taskInfo.setAuthSecret(null);
        return new Result<>(true, "密码重置成功，请使用新密码登录", taskInfo);
    }

    @Override
    @Transactional
    public Result<SecurityTaskInfo> createOtpSession(String uuid) {
        Result<UserSecurityTaskBean> taskResult = loadActiveTask(uuid,
                EnumSet.of(UserSecurityTaskType.RESET_OTP),
                EnumSet.of(UserSecurityTaskStatus.PENDING_OTP));
        if (!taskResult.getStatus()) {
            return new Result<>(false, taskResult.getMessage(), null);
        }
        UserSecurityTaskBean task = taskResult.getData();
        UserBean user = userDao.getUserByCode(task.getUserCode());
        return new Result<>(true, "OTP绑定会话已生成", beginOtpBinding(user, task));
    }

    @Override
    @Transactional
    public Result<String> bindOtp(String uuid, String token, String otpPass) {
        Result<UserSecurityTaskBean> taskResult = loadActiveTask(uuid,
                EnumSet.of(UserSecurityTaskType.ACTIVATE, UserSecurityTaskType.RESET_OTP),
                EnumSet.of(UserSecurityTaskStatus.PENDING_OTP));
        if (!taskResult.getStatus()) {
            return new Result<>(false, taskResult.getMessage(), null);
        }
        UserSecurityTaskBean task = taskResult.getData();
        UserBean user = userDao.getUserByCode(task.getUserCode());
        if (user == null || user.getAuthSecret() == null || user.getAuthSecret().trim().isEmpty()) {
            return new Result<>(false, "OTP绑定会话不存在，请刷新后重试", null);
        }
        if (token == null || token.trim().isEmpty() || !token.equals(user.getToken())) {
            return new Result<>(false, "OTP绑定会话无效，请刷新页面后重试", null);
        }

        int otpCode;
        try {
            otpCode = Integer.parseInt(otpPass);
        } catch (Exception e) {
            return new Result<>(false, "动态码格式错误", null);
        }

        GoogleAuthenticator gAuth = new GoogleAuthenticator();
        if (!Boolean.TRUE.equals(gAuth.authorize(user.getAuthSecret(), otpCode))) {
            return new Result<>(false, "动态码错误，请重新输入", null);
        }

        userDao.completeSecurityTaskOtpByCode(task.getUserCode(), AccountStatus.ACTIVE.name());
        taskDao.markUsed(task.getCode(), new Date());
        return new Result<>(true, "OTP绑定成功，请使用账号密码登录", "success");
    }

    private LinkIssueResult issueTask(UserBean user, UserSecurityTaskType taskType, String createdBy) {
        Date now = new Date();
        taskDao.expirePendingTasksByUser(user.getCode(), now);
        taskDao.cancelPendingTasksByUser(user.getCode());

        String taskUuid = UserSecurityTaskUtils.generateUuid();
        UserSecurityTaskBean task = new UserSecurityTaskBean();
        task.setTaskUuidHash(UserSecurityTaskUtils.hashUuid(taskUuid));
        task.setUserCode(user.getCode());
        task.setTaskType(taskType);
        task.setTaskStatus(initialTaskStatus(taskType));
        task.setExpireTime(Date.from(Instant.now().plus(TASK_EXPIRE_HOURS, ChronoUnit.HOURS)));
        task.setUsedTime(null);
        task.setCreatedBy(createdBy);
        task.setCreatedTime(now);
        taskDao.addTask(task);

        LinkIssueResult result = new LinkIssueResult();
        result.setUserName(user.getUserName());
        result.setEmail(user.getEmail());
        result.setTaskType(taskType);
        result.setExpireTime(task.getExpireTime());
        result.setLinkUrl(String.format("%s/security-task?uuid=%s", trimTrailingSlash(projectHost), taskUuid));
        return result;
    }

    private String resolveCreator(String adminToken) {
        UserBean adminUser = userDao.getUserByToken(adminToken);
        if (adminUser == null || adminUser.getUserName() == null || adminUser.getUserName().trim().isEmpty()) {
            return "admin";
        }
        return adminUser.getUserName();
    }

    private UserSecurityTaskStatus initialTaskStatus(UserSecurityTaskType taskType) {
        if (taskType == UserSecurityTaskType.RESET_OTP) {
            return UserSecurityTaskStatus.PENDING_OTP;
        }
        return UserSecurityTaskStatus.PENDING_PASSWORD;
    }

    private Result<UserSecurityTaskBean> loadActiveTask(String uuid,
                                                        Set<UserSecurityTaskType> taskTypes,
                                                        Set<UserSecurityTaskStatus> taskStatuses) {
        if (uuid == null || uuid.trim().isEmpty()) {
            return new Result<>(false, INVALID_TASK_MESSAGE, null);
        }
        UserSecurityTaskBean task = taskDao.getTaskByHash(UserSecurityTaskUtils.hashUuid(uuid.trim()));
        if (task == null) {
            return new Result<>(false, INVALID_TASK_MESSAGE, null);
        }
        if (task.getExpireTime() != null && task.getExpireTime().before(new Date())
                && task.getTaskStatus() != null
                && EnumSet.of(UserSecurityTaskStatus.PENDING_PASSWORD, UserSecurityTaskStatus.PENDING_OTP)
                .contains(task.getTaskStatus())) {
            taskDao.markExpired(task.getCode());
            task.setTaskStatus(UserSecurityTaskStatus.EXPIRED);
        }
        if (taskTypes != null && !taskTypes.contains(task.getTaskType())) {
            return new Result<>(false, INVALID_TASK_MESSAGE, null);
        }
        if (taskStatuses != null && !taskStatuses.contains(task.getTaskStatus())) {
            return new Result<>(false, resolveTaskStatusMessage(task.getTaskStatus()), null);
        }
        if (task.getTaskStatus() == UserSecurityTaskStatus.EXPIRED
                || task.getTaskStatus() == UserSecurityTaskStatus.USED
                || task.getTaskStatus() == UserSecurityTaskStatus.CANCELLED) {
            return new Result<>(false, resolveTaskStatusMessage(task.getTaskStatus()), null);
        }
        return new Result<>(true, "success", task);
    }

    private String resolveTaskStatusMessage(UserSecurityTaskStatus taskStatus) {
        if (taskStatus == UserSecurityTaskStatus.USED) {
            return "该链接已被使用，请联系管理员重新生成";
        }
        if (taskStatus == UserSecurityTaskStatus.CANCELLED) {
            return "该链接已失效，请使用管理员重新发送的最新链接";
        }
        if (taskStatus == UserSecurityTaskStatus.EXPIRED) {
            return "该链接已过期，请联系管理员重新生成";
        }
        return INVALID_TASK_MESSAGE;
    }

    private SecurityTaskInfo beginOtpBinding(UserBean user, UserSecurityTaskBean task) {
        if (user == null) {
            throw new IllegalStateException("用户不存在");
        }
        if (user.getAuthStatus() == OtpAuthStatus.BINDING
                && user.getToken() != null && !user.getToken().trim().isEmpty()
                && user.getAuthSecret() != null && !user.getAuthSecret().trim().isEmpty()) {
            SecurityTaskInfo taskInfo = toTaskInfo(task);
            taskInfo.setToken(user.getToken());
            taskInfo.setAuthSecret(user.getAuthSecret());
            taskInfo.setTaskStatus(UserSecurityTaskStatus.PENDING_OTP);
            return taskInfo;
        }

        GoogleAuthenticator gAuth = new GoogleAuthenticator();
        GoogleAuthenticatorKey key = gAuth.createCredentials();
        String token = UUID.randomUUID().toString();
        userDao.setUserSecret(key.getKey(), token, user.getUserName());

        SecurityTaskInfo taskInfo = toTaskInfo(task);
        taskInfo.setToken(token);
        taskInfo.setAuthSecret(key.getKey());
        taskInfo.setTaskStatus(UserSecurityTaskStatus.PENDING_OTP);
        return taskInfo;
    }

    private SecurityTaskInfo toTaskInfo(UserSecurityTaskBean task) {
        SecurityTaskInfo info = new SecurityTaskInfo();
        info.setUserName(task.getUserName());
        info.setEmail(task.getEmail());
        info.setTaskType(task.getTaskType());
        info.setTaskStatus(task.getTaskStatus());
        info.setExpireTime(task.getExpireTime());
        if (task.getTaskStatus() == UserSecurityTaskStatus.PENDING_OTP
                && task.getToken() != null && !task.getToken().trim().isEmpty()
                && task.getAuthSecret() != null && !task.getAuthSecret().trim().isEmpty()) {
            info.setToken(task.getToken());
            info.setAuthSecret(task.getAuthSecret());
        }
        return info;
    }

    private String trimTrailingSlash(String host) {
        if (host == null || host.trim().isEmpty()) {
            return "";
        }
        String normalizedHost = host.trim();
        while (normalizedHost.endsWith("/")) {
            normalizedHost = normalizedHost.substring(0, normalizedHost.length() - 1);
        }
        return normalizedHost;
    }
}
