package org.guohai.javasqlweb.service.operation;

import com.zaxxer.hikari.HikariDataSource;
import org.guohai.javasqlweb.beans.ConnectConfigBean;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DbOperationPostgresqlDruidTests {

    @Test
    void cleanupCachedDataSourcesClosesIdlePools() throws Exception {
        DbOperationPostgresqlDruid operation = new DbOperationPostgresqlDruid(buildConnectConfig());
        try {
            HikariDataSource idleDataSource = mock(HikariDataSource.class);
            HikariDataSource activeDataSource = mock(HikariDataSource.class);
            Map<String, Object> postgresMap = accessPostgresMap(operation);
            long now = System.currentTimeMillis();

            postgresMap.put("archive", newCachedDataSource(idleDataSource, now - 20 * 60 * 1000L));
            postgresMap.put("core", newCachedDataSource(activeDataSource, now));

            Method cleanupMethod = DbOperationPostgresqlDruid.class
                    .getDeclaredMethod("cleanupCachedDataSources", long.class, String.class);
            cleanupMethod.setAccessible(true);
            cleanupMethod.invoke(operation, now, "core");

            assertFalse(postgresMap.containsKey("archive"));
            assertTrue(postgresMap.containsKey("core"));
            verify(idleDataSource).close();
        } finally {
            operation.close();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> accessPostgresMap(DbOperationPostgresqlDruid operation) throws Exception {
        Field field = DbOperationPostgresqlDruid.class.getDeclaredField("postgresMap");
        field.setAccessible(true);
        return (Map<String, Object>) field.get(operation);
    }

    private Object newCachedDataSource(HikariDataSource dataSource, long lastAccessAt) throws Exception {
        Class<?> cachedDataSourceClass = Class.forName(DbOperationPostgresqlDruid.class.getName() + "$CachedDataSource");
        Constructor<?> constructor = cachedDataSourceClass.getDeclaredConstructor(javax.sql.DataSource.class, long.class);
        constructor.setAccessible(true);
        return constructor.newInstance(dataSource, lastAccessAt);
    }

    private ConnectConfigBean buildConnectConfig() {
        ConnectConfigBean bean = new ConnectConfigBean();
        bean.setCode(1);
        bean.setDbServerHost("127.0.0.1");
        bean.setDbServerPort("5432");
        bean.setDbServerUsername("postgres");
        bean.setDbServerPassword("postgres");
        bean.setDbServerType("postgresql");
        return bean;
    }
}
