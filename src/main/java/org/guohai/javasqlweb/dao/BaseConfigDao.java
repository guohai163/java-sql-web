package org.guohai.javasqlweb.dao;

import org.apache.ibatis.annotations.*;
import org.guohai.javasqlweb.beans.ConnectConfigBean;
import org.guohai.javasqlweb.beans.QueryLogBean;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

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
    @Select("SELECT code,db_server_name,db_server_type,db_group FROM db_connect_config_tb;")
    List<ConnectConfigBean> getAllConnectConfig();

    /**
     * 获得指定code的连接属性
     * @param code
     * @return
     */
    @Select("SELECT * FROM db_connect_config_tb WHERE code=#{code}")
    ConnectConfigBean getConnectConfig(@Param("code") Integer code);

    /**
     * 保存查询日志
     * @param user
     * @param dbname
     * @param sql
     * @param userIp
     * @param logDate
     * @return
     */
    @Insert("INSERT INTO `db_query_log`\n" +
            "(`query_ip`,\n" +
            "`query_name`,\n" +
            "`query_database`,\n" +
            "`query_sqlscript`,\n" +
            "`query_time`)\n" +
            "VALUES\n" +
            "(#{ip},\n" +
            "#{user},\n" +
            "#{dbname},\n" +
            "#{sql},\n" +
            "#{log_date});")
    Boolean saveQueryLog(@Param("user") String user, @Param("dbname") String dbname,
                         @Param("sql") String sql, @Param("ip") String userIp, @Param("log_date")Date logDate);

    /**
     * 倒序查询日志
     * @return
     */
    @Select("SELECT * FROM db_query_log ORDER BY code DESC LIMIT 2000;")
    List<QueryLogBean> getQueryLog();

    /**
     * 获取所有的连接配置
     * @return
     */
    @Select("SELECT `code`,`db_server_name`,`db_server_host`,`db_server_port`,`db_server_username`," +
            "'' as `db_server_password`,`db_server_type`,`create_time` " +
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
            "`create_time`,`db_group`)\n" +
            "VALUES\n" +
            "(#{server.dbServerName},\n" +
            "#{server.dbServerHost},\n" +
            "#{server.dbServerPort},\n" +
            "#{server.dbServerUsername},\n" +
            "#{server.dbServerPassword},\n" +
            "#{server.dbServerType},\n" +
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
            "`db_group` = #{server.dbGroup}" +
            "WHERE `code` = #{server.code};"+
            "</script>")
    Boolean updateConnServer(@Param("server")ConnectConfigBean server);

    /**
     * 获取数据库分组
     * @return
     */
    @Select("SELECT distinct db_group FROM db_connect_config_tb;")
    List<String> getDbGroup();
}
