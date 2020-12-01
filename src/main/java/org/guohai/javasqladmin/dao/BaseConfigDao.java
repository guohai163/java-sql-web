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
    @Select("SELECT * FROM db_connect_config_tb;")
    List<ConnectConfigBean> getAllConnectConfig();

    @Select("SELECT * FROM db_connect_config_tb WHERE code=#{code}")
    ConnectConfigBean getConnectConfig(@Param("code") Integer code);
}
