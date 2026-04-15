package org.guohai.javasqlweb.service;

import org.guohai.javasqlweb.beans.LinkIssueResult;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.SecurityTaskInfo;

/**
 * 用户安全任务服务
 */
public interface UserSecurityTaskService {

    Result<LinkIssueResult> createActivationTask(String adminToken, String email);

    Result<LinkIssueResult> reissueActivationTask(String adminToken, String userName);

    Result<LinkIssueResult> createPasswordResetTask(String adminToken, String userName);

    Result<LinkIssueResult> createOtpResetTask(String adminToken, String userName);

    Result<SecurityTaskInfo> getTaskInfo(String uuid);

    Result<SecurityTaskInfo> submitPassword(String uuid, String password);

    Result<SecurityTaskInfo> createOtpSession(String uuid);

    Result<String> bindOtp(String uuid, String token, String otpPass);
}
