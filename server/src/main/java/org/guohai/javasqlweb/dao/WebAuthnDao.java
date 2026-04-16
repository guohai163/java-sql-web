package org.guohai.javasqlweb.dao;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.guohai.javasqlweb.beans.WebAuthnBean;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * webauthn操作类
 * @author guohai
 */
@Repository
public interface WebAuthnDao {

    @Insert("insert into passkey_auths_tb (user_name, user_handle, credential_id, public_key, user_agent, create_date)" +
            " values (#{userName},#{userHandle},#{credentialId},#{publicKey},#{userAgent},#{createDate});")
    Boolean addPublicKey(WebAuthnBean webAuthnBean);

    @Select("select user_name from passkey_auths_tb where user_handle=#{userHandle}")
    List<String> getUserName(String userHandle);

    @Select("select * from passkey_auths_tb where user_handle=#{userHandle} AND credential_id=#{credentialId} limit 1")
    WebAuthnBean getWebAuthnBean(String credentialId,String userHandle);

    @Select("SELECT * FROM passkey_auths_tb WHERE credential_id=#{credentialId}")
    List<WebAuthnBean> getAllWebAuthn(String credentialId);
}
