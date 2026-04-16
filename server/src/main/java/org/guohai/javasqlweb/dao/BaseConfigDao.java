package org.guohai.javasqlweb.dao;

import org.apache.ibatis.annotations.*;
import org.guohai.javasqlweb.beans.ConnectConfigBean;
import org.guohai.javasqlweb.beans.QueryLogBean;
import org.guohai.javasqlweb.beans.SqlGuidBean;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 基础信息DAO
 * @author guohai
 */
@Repository
public interface BaseConfigDao {

    /**
     * 返回完整的配置项
     * @return
     */
    @Select("SELECT code,db_server_name,db_server_type,db_ssl_mode,db_group FROM db_connect_config_tb;")
    List<ConnectConfigBean> getAllConnectConfig();

    /**
     * 通过用户ID获取有权限查询的数据库服务器
     * @param userCode
     * @return
     */
    @Select("SELECT DISTINCT c.code,c.db_server_name,c.db_server_type,c.db_ssl_mode,c.db_group FROM user_permissions a " +
            "join db_permissions b on a.group_code=b.group_code " +
            "join db_connect_config_tb c on b.db_code=c.code " +
            "where user_code=#{userCode};")
    List<ConnectConfigBean> getHavePermConnConfig(Integer userCode);

    /**
     * 检查用户是否有指定数据库服务器权限
     * @param userCode 用户编号
     * @param serverCode 服务器编号
     * @return 是否有权限
     */
    @Select("SELECT EXISTS(" +
            "SELECT 1 FROM user_permissions a " +
            "JOIN db_permissions b ON a.group_code=b.group_code " +
            "WHERE a.user_code=#{userCode} AND b.db_code=#{serverCode}" +
            ")")
    Boolean hasServerPermission(@Param("userCode") Integer userCode, @Param("serverCode") Integer serverCode);

    /**
     * 获得指定code的连接属性
     * @param code
     * @return
     */
    @Select("SELECT * FROM db_connect_config_tb WHERE code=#{code}")
    ConnectConfigBean getConnectConfig(@Param("code") Integer code);

    /**
     * 保存查询日志
     * @param queryLog
     * @return
     */
    @Insert("INSERT INTO `db_query_log`\n" +
            "(`query_ip`,\n" +
            "`query_name`,\n" +
            "`query_database`,\n" +
            "`server_code`,\n" +
            "`query_sqlscript`,\n" +
            "`query_time`)\n" +
            "VALUES\n" +
            "(#{queryIp},\n" +
            "#{queryName},\n" +
            "#{queryDatabase},\n" +
            "#{serverCode},\n" +
            "#{querySqlscript},\n" +
            "#{queryTime});")
    @Options(useGeneratedKeys = true, keyProperty = "code", keyColumn = "code")
    Boolean saveQueryLog(QueryLogBean queryLog);

    /**
     * 更新处理统计
     * @param code
     * @param time
     * @param resultRowCount
     * @return
     */
    @Update("UPDATE `db_query_log` SET query_consuming=#{time}, result_row_count=#{resultRowCount} WHERE code=#{code};")
    Boolean updateQueryLogMetrics(@Param("code") Integer code,
                                  @Param("time") Integer time,
                                  @Param("resultRowCount") Integer resultRowCount);

    @Select("<script>" +
            "SELECT l.*, c.db_server_name AS server_name " +
            "FROM db_query_log l " +
            "LEFT JOIN db_connect_config_tb c ON c.code = l.server_code " +
            "<if test='cursorCode != null'>" +
            "WHERE l.code &lt; #{cursorCode} " +
            "</if>" +
            "ORDER BY l.code DESC " +
            "LIMIT #{limit}" +
            "</script>")
    List<QueryLogBean> getQueryLogWindowOlder(@Param("cursorCode") Integer cursorCode,
                                              @Param("limit") Integer limit);

    @Select("<script>" +
            "SELECT l.*, c.db_server_name AS server_name " +
            "FROM db_query_log l " +
            "LEFT JOIN db_connect_config_tb c ON c.code = l.server_code " +
            "WHERE l.code &gt; #{cursorCode} " +
            "ORDER BY l.code ASC " +
            "LIMIT #{limit}" +
            "</script>")
    List<QueryLogBean> getQueryLogWindowNewer(@Param("cursorCode") Integer cursorCode,
                                              @Param("limit") Integer limit);

    @Select("<script>" +
            "SELECT query_log_code, " +
            "GROUP_CONCAT(DISTINCT CONCAT(database_name, '.', table_name) ORDER BY table_name SEPARATOR ', ') AS target_tables " +
            "FROM db_query_log_target_tb " +
            "WHERE query_log_code IN " +
            "<foreach collection='queryLogCodes' item='queryLogCode' open='(' separator=',' close=')'>" +
            "#{queryLogCode}" +
            "</foreach> " +
            "GROUP BY query_log_code" +
            "</script>")
    List<Map<String, Object>> getQueryLogTargetSummaries(@Param("queryLogCodes") List<Integer> queryLogCodes);

    @Select("SELECT EXISTS(SELECT 1 FROM db_query_log WHERE code < #{code})")
    Boolean existsOlderQueryLog(@Param("code") Integer code);

    @Select("SELECT EXISTS(SELECT 1 FROM db_query_log WHERE code > #{code})")
    Boolean existsNewerQueryLog(@Param("code") Integer code);

    /**
     * 获取所有的连接配置
     * @return
     */
    @Select("SELECT `code`,`db_server_name`,`db_server_host`,`db_server_port`,`db_server_username`," +
            "'' as `db_server_password`,`db_server_type`,`db_ssl_mode`,`create_time` " +
            ",`db_group` "+
            "FROM `db_connect_config_tb`;")
    List<ConnectConfigBean> getConnData();

    /**
     * 获得指定name的连接属性
     * @param name
     * @return
     */
    @Select("SELECT * FROM db_connect_config_tb WHERE db_server_name=#{name}")
    ConnectConfigBean getConnectConfigByName(@Param("name") String name);

    /**
     * 获得指定code的连接属性
     * @param code
     * @return
     */
    @Select("SELECT * FROM db_connect_config_tb WHERE code=#{code}")
    ConnectConfigBean getConnectConfigByCode(@Param("code") Integer code);

    /**
     * 删除指定CODE指服务器
     * @param code
     * @return
     */
    @Delete("DELETE FROM `db_connect_config_tb`" +
            "WHERE code=#{code};")
    Boolean delServerByCode(@Param("code") Integer code);

    /**
     * 增加服务器
     * @param server
     * @return
     */
    @Insert("INSERT INTO `db_connect_config_tb`\n" +
            "(`db_server_name`,\n" +
            "`db_server_host`,\n" +
            "`db_server_port`,\n" +
            "`db_server_username`,\n" +
            "`db_server_password`,\n" +
            "`db_server_type`,\n" +
            "`db_ssl_mode`,\n" +
            "`create_time`,`db_group`)\n" +
            "VALUES\n" +
            "(#{server.dbServerName},\n" +
            "#{server.dbServerHost},\n" +
            "#{server.dbServerPort},\n" +
            "#{server.dbServerUsername},\n" +
            "#{server.dbServerPassword},\n" +
            "#{server.dbServerType},\n" +
            "#{server.dbSslMode},\n" +
            "now(),#{server.dbGroup});")
    Boolean addConnServer(@Param("server") ConnectConfigBean server);

    /**
     * 更新服务器信息
     * @param server
     * @return
     */
    @Update("<script>" +
            "UPDATE `db_connect_config_tb`\n" +
            "SET\n" +
            "`db_server_name` = #{server.dbServerName},\n" +
            "`db_server_host` = #{server.dbServerHost},\n" +
            "`db_server_port` = #{server.dbServerPort},\n" +
            "`db_server_username` = #{server.dbServerUsername},\n" +
            "<if test='server.dbServerPassword != null and server.dbServerPassword != \"\"'>" +
            "`db_server_password` = #{server.dbServerPassword}," +
            "</if>" +
            "`db_server_type` = #{server.dbServerType}," +
            "`db_ssl_mode` = #{server.dbSslMode}," +
            "`db_group` = #{server.dbGroup}" +
            "WHERE `code` = #{server.code};"+
            "</script>")
    Boolean updateConnServer(@Param("server")ConnectConfigBean server);

    /**
     * 获取数据库分组
     * @param userCode 用户编号
     * @return
     */
    @Select("SELECT distinct db_group FROM user_permissions a " +
            "join db_permissions b on a.group_code=b.group_code " +
            "join db_connect_config_tb c on b.db_code=c.code " +
            "where user_code=#{userCode}")
    List<String> getDbGroup(Integer userCode);

    /**
     * 获取完整的列表
     * @return
     */
    @Select("select * from guid_sql_tb;")
    List<SqlGuidBean> getSqlGuidAll();
}
