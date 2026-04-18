package org.guohai.javasqlweb.service.webauthn;

import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import org.guohai.javasqlweb.beans.WebAuthnBean;
import org.guohai.javasqlweb.dao.WebAuthnDao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyCredentialRepositoryTests {

    @Mock
    private WebAuthnDao webAuthnDao;

    @InjectMocks
    private MyCredentialRepository credentialRepository;

    @Test
    void lookupReturnsEmptyWhenCredentialDoesNotExist() {
        ByteArray credentialId = ByteArray.fromBase64("Y3JlZGVudGlhbC0x");
        ByteArray userHandle = ByteArray.fromBase64("dXNlci0x");
        when(webAuthnDao.getWebAuthnBean(credentialId.getBase64(), userHandle.getBase64()))
                .thenReturn(null);

        Optional<RegisteredCredential> result = credentialRepository.lookup(credentialId, userHandle);

        assertTrue(result.isEmpty());
    }

    @Test
    void getUsernameForUserHandleReturnsEmptyWhenNoUserExists() {
        ByteArray userHandle = ByteArray.fromBase64("dXNlci0x");
        when(webAuthnDao.getUserName(userHandle.getBase64())).thenReturn(Collections.emptyList());

        Optional<String> result = credentialRepository.getUsernameForUserHandle(userHandle);

        assertTrue(result.isEmpty());
    }

    @Test
    void lookupBuildsRegisteredCredentialWhenRecordExists() {
        ByteArray credentialId = ByteArray.fromBase64("Y3JlZGVudGlhbC0x");
        ByteArray userHandle = ByteArray.fromBase64("dXNlci0x");
        ByteArray publicKey = ByteArray.fromBase64("cHVibGljLWtleS0x");
        when(webAuthnDao.getWebAuthnBean(credentialId.getBase64(), userHandle.getBase64()))
                .thenReturn(new WebAuthnBean(
                        "alice",
                        userHandle.getBase64(),
                        credentialId.getBase64(),
                        publicKey.getBase64(),
                        "JUnit",
                        new Date()
                ));

        Optional<RegisteredCredential> result = credentialRepository.lookup(credentialId, userHandle);

        assertTrue(result.isPresent());
        assertEquals(credentialId, result.get().getCredentialId());
        assertEquals(userHandle, result.get().getUserHandle());
        assertEquals(publicKey, result.get().getPublicKeyCose());
    }

    @Test
    void getUserHandleForUsernameReturnsEmptyForNullUsername() {
        Optional<ByteArray> result = credentialRepository.getUserHandleForUsername(null);

        assertFalse(result.isPresent());
    }
}
