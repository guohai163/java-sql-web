package org.guohai.javasqladmin.dao;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.guohai.javasqladmin.beans.ConnectConfigBean;
import org.springframework.stereotype.Repository;

import java.util.List;

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
}
