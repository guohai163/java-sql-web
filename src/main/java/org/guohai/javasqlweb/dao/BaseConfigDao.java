package org.guohai.javasqlweb.dao;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.guohai.javasqlweb.beans.ConnectConfigBean;
import org.springframework.stereotype.Repository;

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
    @Select("SELECT code,db_server_name,db_server_type FROM db_connect_config_tb;")
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
     * @param sql
     * @return
     */
    @Insert("INSERT INTO `db_query_log`\n" +
            "(`query_ip`,\n" +
            "`query_name`,\n" +
            "`query_sqlscript`,\n" +
            "`query_time`)\n" +
            "VALUES\n" +
            "('127.0.0.1',\n" +
            "#{user},\n" +
            "#{sql},\n" +
            "now());")
    Boolean saveQueryLog(@Param("user") String user,@Param("sql") String sql);
}
