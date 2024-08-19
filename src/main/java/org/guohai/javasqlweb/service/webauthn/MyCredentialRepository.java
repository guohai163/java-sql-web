package org.guohai.javasqlweb.service.webauthn;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;

import java.util.Optional;
import java.util.Set;

public class MyCredentialRepository implements CredentialRepository {
    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String s) {
        return null;
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String s) {
        return Optional.empty();
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray byteArray) {
        return Optional.empty();
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray byteArray, ByteArray byteArray1) {
        return Optional.empty();
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray byteArray) {
        return null;
    }
}
