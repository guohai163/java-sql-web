package org.guohai.javasqlweb.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    private static final char[] HEX_DIGITS = { '0', '1', '2', '3', '4', '5',
            '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };

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

    /**
     * MD5
     * @param input
     * @return
     */
    public static String MD5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(input.getBytes());
            byte[] digest = md.digest();
            int len = digest.length;
            StringBuilder buf = new StringBuilder(len * 2);
            // 把密文转换成十六进制的字符串形式
            for (int j = 0; j < len; j++) {
                buf.append(HEX_DIGITS[(digest[j] >> 4) & 0x0f]);
                buf.append(HEX_DIGITS[digest[j] & 0x0f]);
            }
            return buf.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }
}
