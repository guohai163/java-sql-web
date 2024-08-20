package org.guohai.javasqlweb.service.webauthn;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class MyCredentialRepository implements CredentialRepository {

    private static final Logger LOG  = LoggerFactory.getLogger(MyCredentialRepository.class);

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String s) {
        LOG.info("getCredentialIdsForUsername",s);
        return null;
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String s) {
        LOG.info("getUserHandleForUsername",s);

        return Optional.empty();
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray byteArray) {
        LOG.info("getUsernameForUserHandle",byteArray.toString());

        return Optional.empty();
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray byteArray, ByteArray byteArray1) {
        return Optional.empty();
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray byteArray) {
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
