package org.guohai.javasqlweb.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.guohai.javasqlweb.beans.*;
import org.guohai.javasqlweb.controller.BackstageController;
import org.guohai.javasqlweb.dao.BaseConfigDao;
import org.guohai.javasqlweb.dao.UserManageDao;
import org.guohai.javasqlweb.service.operation.DbOperation;
import org.guohai.javasqlweb.service.operation.DbOperationFactory;
import org.guohai.javasqlweb.util.AccessTokenUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 后台服务
 * @author guohai
 * @date 2021-1-1
 */
@Service
public class BackstageServiceImpl implements BackstageService{

    private static final String LEGACY_TLS_MODE = "LEGACY_TLS";
    @Autowired
    BaseConfigDao baseConfigDao;

    @Autowired
    UserManageDao userDao;

    @Autowired
    DataSource dataSource;

    @Autowired
    HealthEndpoint healthEndpoint;

    @Autowired
    UserSecurityTaskService userSecurityTaskService;

    @org.springframework.beans.factory.annotation.Value("${project.legacy-tls-enabled:false}")
    private boolean legacyTlsEnabled;

    private static final Logger LOG  = LoggerFactory.getLogger(BackstageServiceImpl.class);
    /**
     * 获取查询日志
     *
     * @return
     */
    @Override
    public Result<List<QueryLogBean>> getQueryLog() {

        return new Result<>(true,"", baseConfigDao.getQueryLog());
    }

    /**
     * 获取连接表
     *
     * @return
     */
    @Override
    public Result<List<ConnectConfigBean>> getConnData() {
        List<ConnectConfigBean> listConn = baseConfigDao.getConnData();

        return new Result<>(true, "", listConn);
    }

    @Override
    public Result<List<PoolStatBean>> getPoolStats() {
        HikariDataSource hikariDataSource = unwrapHikariDataSource();
        if (hikariDataSource == null) {
            return new Result<>(false, "当前数据源不是 HikariDataSource", null);
        }

        PoolStatBean bean = new PoolStatBean();
        bean.setPoolName(hikariDataSource.getPoolName());
        bean.setJdbcUrl(hikariDataSource.getJdbcUrl());
        bean.setDriverClassName(hikariDataSource.getDriverClassName());
        bean.setHealthStatus(healthEndpoint.health().getStatus().getCode());

        HikariPoolMXBean poolMxBean = hikariDataSource.getHikariPoolMXBean();
        if (poolMxBean != null) {
            bean.setActiveConnections(poolMxBean.getActiveConnections());
            bean.setIdleConnections(poolMxBean.getIdleConnections());
            bean.setTotalConnections(poolMxBean.getTotalConnections());
            bean.setThreadsAwaitingConnection(poolMxBean.getThreadsAwaitingConnection());
        }
        return new Result<>(true, "", Collections.singletonList(bean));
    }

    /**
     * 测试数据库连接性
     *
     * @param server 服务器信息
     * @return
     */
    @Override
    public Result<String> testServerConnect(ConnectConfigBean server) {
        if ("mssql".equalsIgnoreCase(server.getDbServerType())
                && LEGACY_TLS_MODE.equalsIgnoreCase(server.getDbSslMode())
                && !legacyTlsEnabled) {
            return new Result<>(false,
                    "当前服务器配置为 LEGACY_TLS，但应用未启用 project.legacy-tls-enabled。请先在部署层显式开启遗留 TLS 模式。",
                    "当前服务器配置为 LEGACY_TLS，但应用未启用 project.legacy-tls-enabled。请先在部署层显式开启遗留 TLS 模式。");
        }
        try {
            DbOperation dbOperation  = DbOperationFactory.createDbOperation(server);
            dbOperation.serverHealth();
        } catch (Exception e) {
            LOG.error(e.getMessage());
            e.printStackTrace();
            if ("mssql".equalsIgnoreCase(server.getDbServerType())
                    && e.getMessage() != null
                    && e.getMessage().contains("TLS10 is not accepted by client preferences")) {
                String message;
                if (LEGACY_TLS_MODE.equalsIgnoreCase(server.getDbSslMode())) {
                    message = "目标 SQL Server 仍在要求 TLS 1.0。请检查部署是否已为 JVM 显式开启 TLS1.0 兼容参数，或确认 SQL Server 的 Force Encryption / 证书配置。";
                } else {
                    message = "目标 SQL Server 仅支持 TLS 1.0。若该库位于可信内网且必须兼容，可将连接安全改为 LEGACY_TLS；若服务器启用了 Force Encryption，则需由 DBA 关闭强制加密或升级 TLS。";
                }
                return new Result<>(false, message, message);
            }
            return new Result<>(false,e.getMessage(),e.getMessage());
        }
        return new Result<>(true,"连接成功","");
    }

    /**
     * 增加服务器
     *
     * @param server
     * @return
     */
    @Override
    public Result<String> addConnServer(ConnectConfigBean server) {
        if(null != baseConfigDao.getConnectConfigByName(server.getDbServerName())){
            return new Result<>(false,"","同名服务器已经存在");
        }
        baseConfigDao.addConnServer(server);
        return new Result<>(true, "","服务器增加成功");
    }

    /**
     * 获取站点基础数据
     *
     * @return
     */
    @Override
    public Result<Map> getSiteBaseData() {
        Map<String,Object> baseData = new HashMap<String, Object>(2);
        baseData.put("user_count", userDao.getUserList().size());
        baseData.put("server_count", baseConfigDao.getAllConnectConfig().size());
        return new Result<>(true, "", baseData);
    }

    /**
     * 获取完整用户数据
     *
     * @return
     */
    @Override
    public Result<List<UserBean>> getUserData() {
        List<UserBean> userList = userDao.getUserListWithAccessToken();
        userList.forEach(this::enrichUserAccessTokenStatus);
        return new Result<>(true, "", userList);
    }

    /**
     * 增加新用户
     *
     * @param user 用户
     * @return
     */
    @Override
    public Result<LinkIssueResult> addNewUser(String token, UserBean user) {
        return userSecurityTaskService.createActivationTask(token, user.getEmail());
    }

    /**
     * 删除指定用户
     *
     * @param userName
     * @return
     */
    @Override
    public Result<String> delUser(String userName) {
        if(null == userDao.getUserByName(userName)){
            return new Result<>(false, "","用户不存在" ) ;
        }
        userDao.delUser(userName);
        return new Result<>(true, "","删除成功");
    }

    /**
     * 删除指定服务器连接
     *
     * @param code
     * @return
     */
    @Override
    public Result<String> delServer(Integer code) {
        if(null == baseConfigDao.getConnectConfig(code)){
            return new Result<>(false, "","无此服务器" ) ;
        }
        baseConfigDao.delServerByCode(code);
        return new Result<>(true, "","删除成功");
    }

    /**
     * 通过有效token修改用户密码
     *
     * @param token
     * @param newPass
     * @return
     */
    @Override
    public Result<LinkIssueResult> reissueActivationLink(String token, String userName) {
        return userSecurityTaskService.reissueActivationTask(token, userName);
    }

    /**
     * 管理员为用户发起密码重置
     * @param token 管理员登录态
     * @param userName 用户名
     * @return 结果
     */
    @Override
    public Result<LinkIssueResult> resetUserPassword(String token, String userName) {
        return userSecurityTaskService.createPasswordResetTask(token, userName);
    }

    /**
     * 管理员为用户发起OTP重绑
     * @param token 管理员登录态
     * @param userName 用户名
     * @return 结果
     */
    @Override
    public Result<LinkIssueResult> resetUserOtp(String token, String userName) {
        return userSecurityTaskService.createOtpResetTask(token, userName);
    }

    /**
     * 更新服务器数据
     *
     * @param server
     * @return
     */
    @Override
    public Result<String> updateServerData(ConnectConfigBean server) {
        if(null == baseConfigDao.getConnectConfigByCode(server.getCode())){
            return new Result<>(false, "","服务器不存在" );
        }
        baseConfigDao.updateConnServer(server);
        return new Result<>(true, "","修改成功");
    }

    private void enrichUserAccessTokenStatus(UserBean user) {
        user.setHasAccessToken(AccessTokenUtils.hasAccessToken(user));
        user.setAccessTokenStatus(AccessTokenUtils.resolveStatus(user));
        user.setAccessToken(null);
        user.setMaskedAccessToken(null);
        user.setAccessTokenFullVisible(false);
        user.setCanCreateAccessToken(null);
        user.setCanRenewAccessToken(null);
        user.setCanResetAccessToken(null);
    }

    private HikariDataSource unwrapHikariDataSource() {
        if (dataSource instanceof HikariDataSource) {
            return (HikariDataSource) dataSource;
        }
        try {
            return dataSource.unwrap(HikariDataSource.class);
        } catch (SQLException e) {
            LOG.warn("Unable to unwrap HikariDataSource", e);
            return null;
        }
    }
}
