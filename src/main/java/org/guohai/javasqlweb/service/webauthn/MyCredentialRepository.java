package org.guohai.javasqlweb.service.webauthn;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class MyCredentialRepository implements CredentialRepository {

    private static final Logger LOG  = LoggerFactory.getLogger(MyCredentialRepository.class);



    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String userName) {
        LOG.info("getCredentialIdsForUsername",userName);
        return null;
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String userName) {
        LOG.info("getUserHandleForUsername",userName);

        return Optional.empty();
    }

    /**
     *
     * @param userHandle
     * @return
     */
    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        LOG.info("getUsernameForUserHandle",userHandle.toString());

        return Optional.empty();
    }

    /**
     * 通过credentialId和用户来查询
     * @param credentialId
     * @param userHandle
     * @return
     */
    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        return Optional.empty();
    }

    /**
     * 通过credentialId查询库内所有数据
     * @param credentialId
     * @return
     */
    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        List<String> listAuth = new ArrayList<>(2);
        return listAuth.stream()
                .map(
                        credential ->
                                RegisteredCredential.builder()
                                        .credentialId(ByteArray.fromBase64(credential))
                                        .userHandle(ByteArray.fromBase64(credential))
                                        .publicKeyCose(ByteArray.fromBase64(credential))
                                        .signatureCount(1)
                                        .build()
                ).collect(Collectors.toSet());

    }
}
