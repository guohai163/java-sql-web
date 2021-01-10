package org.guohai.javasqlweb.util;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 工具类
 * @author guohai
 * @date 2021-1-1
 */
public class Utils {

    /**
     * 关闭连接，释放连接回连接池
     * @param resultSet
     * @param statement
     * @param connection
     */
    public static void  closeResource(ResultSet resultSet, Statement statement, Connection connection){
        try {
            if (resultSet != null) {
                resultSet.close();
            }
            if (statement != null) {
                statement.close();
            }
            if(null != connection){
                connection.close();
            }
        } catch (SQLException throwable) {
            throwable.printStackTrace();
        }

    }
}
