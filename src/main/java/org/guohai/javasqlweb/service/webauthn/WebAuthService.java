package org.guohai.javasqlweb.service.webauthn;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.beans.UserLoginStatus;

import org.guohai.javasqlweb.dao.UserManageDao;
import org.guohai.javasqlweb.service.BackstageServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;


@Service
public class WebAuthService {

    RelyingPartyIdentity rpIdentity = RelyingPartyIdentity.builder()
            .id("localhost")  // Set this to a parent domain that covers all subdomains
            // where users' credentials should be valid
            .name("Java Sql Web App")
            .build();

    RelyingParty rp = RelyingParty.builder()
            .identity(rpIdentity)
            .credentialRepository(new MyCredentialRepository())
            .build();
    private static final Logger LOG  = LoggerFactory.getLogger(WebAuthService.class);
    /**
     * 管理DAO
     */
    @Autowired
    UserManageDao userDao;
    private Random random =new Random();

    private Map<String, PublicKeyCredentialCreationOptions> mapWebAuthnCreate = new HashMap<>(20);

    /**
     * 创建CR时使用
     * @param token
     * @return
     */
    public Result<String> create(String token){
        UserBean user = userDao.getUserByToken(token);
        if(null == user){
            // 失败
            return new Result<>(false,"未登录",null);
        }
        if(UserLoginStatus.LOGGED != user.getLoginStatus()){
            // 非登录完成状态
            return new Result<>(false,"未登录", null);
        }
        PublicKeyCredentialCreationOptions request = rp.startRegistration(
                StartRegistrationOptions.builder()
                        .user(
                                findExistingUser(user.getUserName())
                                        .orElseGet(() -> {
                                            byte[] userHandle = new byte[64];
                                            random.nextBytes(userHandle);
                                            return UserIdentity.builder()
                                                    .name(user.getUserName())
                                                    .displayName(user.getUserName())
                                                    .id(new ByteArray(userHandle))
                                                    .build();
                                        })
                        )
                        .build());

        try {
            String credentialCreateJson = request.toCredentialsCreateJson();
            mapWebAuthnCreate.put(token,request);
            return new Result<>(true,"创建CR成功",credentialCreateJson);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 存储passkey令牌
     * @param token
     * @param publicKeyCredentialJson
     * @return
     * @throws IOException
     */
    public Result<String> register(String token,String publicKeyCredentialJson) throws IOException {
        PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> pkc =
                PublicKeyCredential.parseRegistrationResponseJson(publicKeyCredentialJson);

        try {
            RegistrationResult result = rp.finishRegistration(FinishRegistrationOptions.builder()
                    .request(mapWebAuthnCreate.get(token))  // The PublicKeyCredentialCreationOptions from startRegistration above
                    // NOTE: Must be stored in server memory or otherwise protected against tampering
                    .response(pkc)
                    .build());
            //验证结果准备入库中
            LOG.info(result.toString());
            String inDB = "";
        } catch (RegistrationFailedException e) {
            LOG.error(e.toString());
        }
        return null;
    }


    /**
     * 登录准备
     * @return
     * @throws JsonProcessingException
     */
    public Result<String> get() throws JsonProcessingException {
        AssertionRequest request = rp.startAssertion(StartAssertionOptions.builder()
                .build());
        String credentialGetJson = request.toCredentialsGetJson();
        return new Result<>(true,"成功", credentialGetJson);
    }

    public Result<String> signIn(String publicKeyCredentialJson) throws IOException {
        PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> pkc =
                PublicKeyCredential.parseAssertionResponseJson(publicKeyCredentialJson);

        String crId ;
//        try {
//            AssertionResult result = rp.finishAssertion(FinishAssertionOptions.builder()
//                    .request(request)  // The PublicKeyCredentialRequestOptions from startAssertion above
//                    .response(pkc)
//                    .build());
//
//            if (result.isSuccess()) {
//                // 处理登录成功部分
//            }
//        } catch (AssertionFailedException e) { /* ... */ }
//        throw new RuntimeException("Authentication failed");
            return null;
    }




    private Optional<UserIdentity> findExistingUser(String username) {
         return Optional.ofNullable(UserIdentity.builder()
                 .name(username)
                 .displayName(username)
                 .id(new ByteArray("aaa".getBytes()))
                 .build());
    }

}
