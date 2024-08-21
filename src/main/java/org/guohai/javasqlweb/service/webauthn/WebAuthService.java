package org.guohai.javasqlweb.service.webauthn;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import org.guohai.javasqlweb.beans.Result;
import org.guohai.javasqlweb.beans.UserBean;
import org.guohai.javasqlweb.beans.UserLoginStatus;

import org.guohai.javasqlweb.beans.WebAuthnBean;
import org.guohai.javasqlweb.dao.UserManageDao;
import org.guohai.javasqlweb.dao.WebAuthnDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;

/**
 * https://blog.csdn.net/qq_31650157/article/details/128938220
 */
@Service
public class WebAuthService {



    RelyingParty rp ;

    private static final Logger LOG  = LoggerFactory.getLogger(WebAuthService.class);
    /**
     * 管理DAO
     */
    @Autowired
    UserManageDao userDao;

    @Autowired
    WebAuthnDao webAuthnDao;

    @Autowired
    HttpServletRequest httpServletRequest;

    private Random random =new Random();

    private Map<String, PublicKeyCredentialCreationOptions> mapWebAuthnCreate = new HashMap<>(20);

    /**
     * 登录用户
     */
    private Map<String, AssertionRequest> mapAssertionRequest = new HashMap<>(20);


    public WebAuthService(@Value("${project.domain}") String domain,@Value("${project.host}")String host,MyCredentialRepository myCredentialRepository){
        Set<String> setStr = new HashSet<>(2);
        setStr.add(host);

        RelyingPartyIdentity rpIdentity = RelyingPartyIdentity.builder()
                .id(domain)  // Set this to a parent domain that covers all subdomains
                // where users' credentials should be valid
                .name("Java Sql Web App")
                .build();
        rp = RelyingParty.builder()
                .identity(rpIdentity)
                .credentialRepository(myCredentialRepository)
                .origins(setStr)
                .build();
    }
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

        UserIdentity userIdentity = UserIdentity.builder()
                .name(user.getUserName())
                .displayName(user.getUserName())
                .id(new ByteArray(user.getUserName().getBytes()))
                .build();

        PublicKeyCredentialCreationOptions request = rp.startRegistration(
                StartRegistrationOptions.builder()
                        .user(userIdentity)
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
        if(publicKeyCredentialJson.isEmpty()){
            return new Result<>(false,"请求串为空",null);
        }
        PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> pkc =
                PublicKeyCredential.parseRegistrationResponseJson(publicKeyCredentialJson);

        try {
            PublicKeyCredentialCreationOptions userPKCC = mapWebAuthnCreate.get(token);
            RegistrationResult result = rp.finishRegistration(FinishRegistrationOptions.builder()
                    .request(userPKCC)  // The PublicKeyCredentialCreationOptions from startRegistration above
                    // NOTE: Must be stored in server memory or otherwise protected against tampering
                    .response(pkc)
                    .build());
            //验证结果准备入库中
            LOG.info(result.toString());
            // 验证通过了，


                UserBean user = userDao.getUserByToken(token);

                WebAuthnBean webAuthnBean = new WebAuthnBean( user.getUserName(),
                        userPKCC.getUser().getId().getBase64(),
                        result.getKeyId().getId().getBase64(),
                        result.getPublicKeyCose().getBase64(),
                        httpServletRequest.getHeader("User-Agent"),
                        new Date());
                return new Result<>( webAuthnDao.addPublicKey(webAuthnBean),"",null);


        } catch (RegistrationFailedException e) {
            LOG.error(e.toString());
        }
        return new Result<>(false,"处理失败",null);
    }


    /**
     * 登录准备
     * @return
     * @throws JsonProcessingException
     */
    public Result<String> get(String sessionKey) throws JsonProcessingException {
        AssertionRequest request = rp.startAssertion(StartAssertionOptions.builder()
                .build());
        mapAssertionRequest.put(sessionKey, request);
        String credentialGetJson = request.toCredentialsGetJson();

        return new Result<>(true,"成功", credentialGetJson);
    }

    public Result<UserBean> signIn(String publicKeyCredentialJson,String sessionKey) throws IOException {
        PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> pkc =
                PublicKeyCredential.parseAssertionResponseJson(publicKeyCredentialJson);
        try {
            AssertionResult result = rp.finishAssertion(FinishAssertionOptions.builder()
                    .request(mapAssertionRequest.get(sessionKey))  // The PublicKeyCredentialRequestOptions from startAssertion above
                    .response(pkc)
                    .build());

            if (result.isSuccess()) {
                LOG.info(result.getUsername());
                UserBean user =  userDao.getUserByName(result.getUsername());
                user.setToken(UUID.randomUUID().toString());
                if(userDao.setUserToken(user.getUserName(),user.getToken())){
                    userDao.setUserLoginSuccess(user.getToken());
                    return new Result<>(true,"success", user);
                }
            }
        } catch (AssertionFailedException e) {
            LOG.error(e.toString());
            return new Result<>(false, e.toString(),null);
        }

        return new Result<>(false, "登录失败",null);
    }


}
