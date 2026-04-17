package org.guohai.javasqlweb.dao;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.guohai.javasqlweb.beans.WebAuthnRequestBean;
import org.guohai.javasqlweb.beans.WebAuthnRequestType;
import org.springframework.stereotype.Repository;

import java.util.Date;

/**
 * WebAuthn 一次性请求存储
 */
@Repository
public interface WebAuthnRequestDao {

    /**
     * 保存或覆盖 WebAuthn 请求
     * @param request 请求
     * @return 是否成功
     */
    @Insert("INSERT INTO webauthn_request_tb (request_type,request_key,request_json,expire_time,created_time) " +
            "VALUES (#{request.requestType},#{request.requestKey},#{request.requestJson},#{request.expireTime},#{request.createdTime}) " +
            "ON DUPLICATE KEY UPDATE request_json=VALUES(request_json),expire_time=VALUES(expire_time),created_time=VALUES(created_time)")
    Boolean saveRequest(@Param("request") WebAuthnRequestBean request);

    /**
     * 删除同类型同 key 的旧请求
     * @param requestType 请求类型
     * @param requestKey 请求 key
     * @return 删除数量
     */
    @Delete("DELETE FROM webauthn_request_tb WHERE request_type=#{requestType} AND request_key=#{requestKey}")
    Integer deleteByTypeAndKey(@Param("requestType") WebAuthnRequestType requestType,
                               @Param("requestKey") String requestKey);

    /**
     * 删除过期请求
     * @param now 当前时间
     * @return 删除数量
     */
    @Delete("DELETE FROM webauthn_request_tb WHERE expire_time <= #{now}")
    Integer deleteExpired(@Param("now") Date now);

    /**
     * 删除指定 key 上的过期请求
     * @param requestType 请求类型
     * @param requestKey 请求 key
     * @param now 当前时间
     * @return 删除数量
     */
    @Delete("DELETE FROM webauthn_request_tb WHERE request_type=#{requestType} AND request_key=#{requestKey} AND expire_time <= #{now}")
    Integer deleteExpiredByTypeAndKey(@Param("requestType") WebAuthnRequestType requestType,
                                      @Param("requestKey") String requestKey,
                                      @Param("now") Date now);

    /**
     * 查询仍有效的请求并加锁
     * @param requestType 请求类型
     * @param requestKey 请求 key
     * @param now 当前时间
     * @return 请求
     */
    @Select("SELECT code,request_type,request_key,request_json,expire_time,created_time " +
            "FROM webauthn_request_tb " +
            "WHERE request_type=#{requestType} AND request_key=#{requestKey} AND expire_time > #{now} " +
            "LIMIT 1 FOR UPDATE")
    WebAuthnRequestBean getActiveRequestForUpdate(@Param("requestType") WebAuthnRequestType requestType,
                                                  @Param("requestKey") String requestKey,
                                                  @Param("now") Date now);

    /**
     * 按主键删除请求
     * @param code 主键
     * @return 删除数量
     */
    @Delete("DELETE FROM webauthn_request_tb WHERE code=#{code}")
    Integer deleteByCode(@Param("code") Integer code);
}
