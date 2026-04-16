package org.guohai.javasqlweb.config;

import org.guohai.javasqlweb.beans.AccountStatus;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.dao.UserManageDao;
import org.guohai.javasqlweb.util.PasswordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 首次启动时自动补齐管理员账号。
 */
@Component
public class AdminBootstrapInitializer implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AdminBootstrapInitializer.class);
    private static final String ADMIN_USER_NAME = "admin";
    private static final String ADMIN_EMAIL = "admin@local.invalid";

    private final UserManageDao userManageDao;

    public AdminBootstrapInitializer(UserManageDao userManageDao) {
        this.userManageDao = userManageDao;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            UserBean adminUser = userManageDao.getUserByName(ADMIN_USER_NAME);
            if (adminUser != null) {
                return;
            }

            String randomPassword = PasswordUtils.randomTemporaryPassword();
            boolean created = userManageDao.addNewUser(
                    ADMIN_USER_NAME,
                    ADMIN_EMAIL,
                    PasswordUtils.encode(randomPassword),
                    AccountStatus.ACTIVE.name()
            );
            if (!created) {
                LOG.warn("Admin bootstrap skipped because admin user creation returned false.");
                return;
            }

            LOG.warn("============================================================");
            LOG.warn("Bootstrap admin account created.");
            LOG.warn("Username: {}", ADMIN_USER_NAME);
            LOG.warn("Password: {}", randomPassword);
            LOG.warn("Please sign in immediately and bind OTP.");
            LOG.warn("You can inspect this password later via docker logs.");
            LOG.warn("============================================================");
        } catch (Exception e) {
            LOG.warn("Admin bootstrap initializer skipped: {}", e.getMessage());
        }
    }
}
