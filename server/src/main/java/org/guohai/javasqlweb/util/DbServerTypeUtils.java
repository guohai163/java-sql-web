package org.guohai.javasqlweb.util;

import org.guohai.javasqlweb.beans.ConnectConfigBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 数据库类型归一化工具
 */
public final class DbServerTypeUtils {

    public static final String MYSQL = "mysql";
    public static final String MARIADB = "mariadb";
    public static final String MSSQL = "mssql";
    public static final String POSTGRESQL = "postgresql";
    public static final String CLICKHOUSE = "clickhouse";

    private DbServerTypeUtils() {
    }

    public static String normalize(String dbServerType) {
        if (dbServerType == null) {
            return "";
        }
        String normalized = dbServerType.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "mssql":
            case "mssql_druid":
            case "sqlserver":
                return MSSQL;
            case "mysql":
                return MYSQL;
            case "mariadb":
                return MARIADB;
            case "postgres":
            case "pgsql":
            case "postgresql":
                return POSTGRESQL;
            case "clickhouce":
            case "clickhouse":
            case "ck":
                return CLICKHOUSE;
            default:
                return normalized;
        }
    }

    public static String displayName(String dbServerType) {
        String normalized = normalize(dbServerType);
        switch (normalized) {
            case MYSQL:
                return "mysql";
            case MARIADB:
                return "mariadb";
            case MSSQL:
                return "mssql";
            case POSTGRESQL:
                return "pgsql";
            case CLICKHOUSE:
                return "clickhouse";
            default:
                return normalized;
        }
    }

    public static boolean isMysqlFamily(String dbServerType) {
        String normalized = normalize(dbServerType);
        return MYSQL.equals(normalized) || MARIADB.equals(normalized);
    }

    public static ConnectConfigBean normalize(ConnectConfigBean bean) {
        if (bean == null) {
            return null;
        }
        bean.setDbServerType(normalize(bean.getDbServerType()));
        return bean;
    }

    public static List<ConnectConfigBean> normalize(List<ConnectConfigBean> beans) {
        if (beans == null) {
            return null;
        }
        List<ConnectConfigBean> normalizedBeans = new ArrayList<>(beans.size());
        for (ConnectConfigBean bean : beans) {
            normalizedBeans.add(normalize(bean));
        }
        return normalizedBeans;
    }
}
