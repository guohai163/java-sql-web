package org.guohai.javasqlweb.dao;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.guohai.javasqlweb.beans.OidcLoginStateBean;
import org.springframework.stereotype.Repository;

import java.util.Date;

/**
 * OIDC 登录 state 持久化 DAO
 */
@Repository
public interface OidcLoginStateDao {

    /**
     * 保存或覆盖指定用途下的 state 记录
     * @param stateRecord state 记录
     * @return 是否成功
     */
    @Insert.List({
            @Insert(value = "INSERT INTO oidc_login_state_tb (usage_type,state_key,code_verifier,expire_time,created_time) " +
                    "VALUES (#{stateRecord.usageType},#{stateRecord.stateKey},#{stateRecord.codeVerifier},#{stateRecord.expireTime},#{stateRecord.createdTime}) " +
                    "ON DUPLICATE KEY UPDATE code_verifier=VALUES(code_verifier),expire_time=VALUES(expire_time),created_time=VALUES(created_time)", databaseId = "mysql"),
            @Insert(value = "INSERT INTO oidc_login_state_tb (usage_type,state_key,code_verifier,expire_time,created_time) " +
                    "VALUES (#{stateRecord.usageType},#{stateRecord.stateKey},#{stateRecord.codeVerifier},#{stateRecord.expireTime},#{stateRecord.createdTime}) " +
                    "ON CONFLICT (usage_type,state_key) DO UPDATE SET " +
                    "code_verifier=EXCLUDED.code_verifier,expire_time=EXCLUDED.expire_time,created_time=EXCLUDED.created_time", databaseId = "postgresql")
    })
    Boolean saveState(@Param("stateRecord") OidcLoginStateBean stateRecord);

    /**
     * 删除过期 state
     * @param now 当前时间
     * @return 删除数量
     */
    @Delete("DELETE FROM oidc_login_state_tb WHERE expire_time <= #{now}")
    Integer deleteExpired(@Param("now") Date now);

    /**
     * 查询指定用途下仍有效的 state，并加锁防止重复消费
     * @param usageType state 用途
     * @param stateKey OIDC state
     * @param now 当前时间
     * @return state 记录
     */
    @Select("SELECT code,usage_type,state_key,code_verifier,expire_time,created_time " +
            "FROM oidc_login_state_tb " +
            "WHERE usage_type=#{usageType} AND state_key=#{stateKey} AND expire_time > #{now} " +
            "LIMIT 1 FOR UPDATE")
    OidcLoginStateBean getActiveStateForUpdate(@Param("usageType") String usageType,
                                               @Param("stateKey") String stateKey,
                                               @Param("now") Date now);

    /**
     * 删除指定主键的 state 记录
     * @param code 主键
     * @return 删除数量
     */
    @Delete("DELETE FROM oidc_login_state_tb WHERE code=#{code}")
    Integer deleteByCode(@Param("code") Integer code);

    /**
     * 删除指定用途和 state 上已过期的记录
     * @param usageType state 用途
     * @param stateKey OIDC state
     * @param now 当前时间
     * @return 删除数量
     */
    @Delete("DELETE FROM oidc_login_state_tb WHERE usage_type=#{usageType} AND state_key=#{stateKey} AND expire_time <= #{now}")
    Integer deleteExpiredByUsageAndState(@Param("usageType") String usageType,
                                         @Param("stateKey") String stateKey,
                                         @Param("now") Date now);
}
