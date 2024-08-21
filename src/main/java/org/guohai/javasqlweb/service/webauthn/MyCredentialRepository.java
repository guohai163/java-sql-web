package org.guohai.javasqlweb.service.webauthn;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import org.guohai.javasqlweb.beans.WebAuthnBean;
import org.guohai.javasqlweb.dao.WebAuthnDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;


@Repository
public class MyCredentialRepository implements CredentialRepository {

    private static final Logger LOG  = LoggerFactory.getLogger(MyCredentialRepository.class);

    @Autowired
    WebAuthnDao webAuthnDao;

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String userName) {
        LOG.info("getCredentialIdsForUsername",userName);


        Set<PublicKeyCredentialDescriptor> seta = new HashSet<>(2);
        seta.add(PublicKeyCredentialDescriptor.builder()
                .id( new ByteArray(userName.getBytes()))
                .build());
        return seta;

    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String userName) {
        LOG.info("getUserHandleForUsername",userName);
        return Optional.of( new ByteArray(userName.getBytes()));
    }

    /**
     *
     * @param userHandle
     * @return
     */
    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        List<String> listUser  = webAuthnDao.getUserName(userHandle.getBase64());
        return Optional.of(listUser.get(0));
    }

    /**
     * 通过credentialId和用户来查询
     * @param credentialId
     * @param userHandle
     * @return
     */
    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        LOG.info("lookup");
        WebAuthnBean webAuthnBean = webAuthnDao.getWebAuthnBean(credentialId.getBase64(), userHandle.getBase64());
        Optional<WebAuthnBean> auth = Optional.of(webAuthnBean);
        return auth.map(
                credential ->
                        RegisteredCredential.builder()
                                .credentialId(ByteArray.fromBase64(credential.getCredentialId()))
                                .userHandle(ByteArray.fromBase64(credential.getUserHandle()))
                                .publicKeyCose(ByteArray.fromBase64(credential.getPublicKey()))
                                .build()
        );
    }

    /**
     * 通过credentialId查询库内所有数据
     * @param credentialId
     * @return
     */
    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        List<WebAuthnBean> listAuth = webAuthnDao.getAllWebAuthn(credentialId.getBase64())  ;
        return listAuth.stream()
                .map(
                        credential ->
                                RegisteredCredential.builder()
                                        .credentialId(ByteArray.fromBase64(credential.getCredentialId()))
                                        .userHandle(ByteArray.fromBase64(credential.getUserHandle()))
                                        .publicKeyCose(ByteArray.fromBase64(credential.getPublicKey()))
                                        .signatureCount(listAuth.size())
                                        .build()
                ).collect(Collectors.toSet());

    }
}
