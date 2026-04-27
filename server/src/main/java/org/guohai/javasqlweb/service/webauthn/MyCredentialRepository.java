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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


@Repository
public class MyCredentialRepository implements CredentialRepository {

    private static final Logger LOG = LoggerFactory.getLogger(MyCredentialRepository.class);

    @Autowired
    WebAuthnDao webAuthnDao;

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String userName) {
        if (userName == null || userName.isBlank()) {
            return Set.of();
        }
        List<String> credentialIds = webAuthnDao.getCredentialIdByUserName(userName);
        if (credentialIds == null || credentialIds.isEmpty()) {
            return Set.of();
        }
        return credentialIds.stream()
                .map(this::parseStoredByteArray)
                .flatMap(Optional::stream)
                .map(credentialId -> PublicKeyCredentialDescriptor.builder().id(credentialId).build())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String userName) {
        if (userName == null || userName.isBlank()) {
            return Optional.empty();
        }
        List<String> userHandles = webAuthnDao.getUserHandleByUserName(userName);
        if (userHandles == null || userHandles.isEmpty()) {
            return Optional.empty();
        }
        return userHandles.stream()
                .map(this::parseStoredByteArray)
                .flatMap(Optional::stream)
                .findFirst();
    }

    /**
     *
     * @param userHandle
     * @return
     */
    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        for (String userHandleCandidate : encodeCandidates(userHandle)) {
            List<String> listUser = webAuthnDao.getUserName(userHandleCandidate);
            if (listUser != null && !listUser.isEmpty()) {
                return Optional.ofNullable(listUser.get(0));
            }
        }
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
        for (String credentialIdCandidate : encodeCandidates(credentialId)) {
            for (String userHandleCandidate : encodeCandidates(userHandle)) {
                WebAuthnBean webAuthnBean = webAuthnDao.getWebAuthnBean(credentialIdCandidate, userHandleCandidate);
                Optional<RegisteredCredential> credential = buildRegisteredCredential(webAuthnBean);
                if (credential.isPresent()) {
                    return credential;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 通过credentialId查询库内所有数据
     * @param credentialId
     * @return
     */
    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        Set<RegisteredCredential> allCredentials = new LinkedHashSet<>();
        for (String credentialIdCandidate : encodeCandidates(credentialId)) {
            List<WebAuthnBean> listAuth = webAuthnDao.getAllWebAuthn(credentialIdCandidate);
            if (listAuth == null || listAuth.isEmpty()) {
                continue;
            }
            listAuth.stream()
                    .map(this::buildRegisteredCredential)
                    .flatMap(Optional::stream)
                    .forEach(allCredentials::add);
        }
        if (allCredentials.isEmpty()) {
            LOG.warn("Passkey credential not found. credentialIdHex={}", credentialId.getHex());
        }
        return allCredentials;
    }

    private Optional<RegisteredCredential> buildRegisteredCredential(WebAuthnBean credential) {
        if (credential == null) {
            return Optional.empty();
        }
        Optional<ByteArray> credentialId = parseStoredByteArray(credential.getCredentialId());
        Optional<ByteArray> userHandle = parseStoredByteArray(credential.getUserHandle());
        Optional<ByteArray> publicKey = parseStoredByteArray(credential.getPublicKey());
        if (credentialId.isEmpty() || userHandle.isEmpty() || publicKey.isEmpty()) {
            LOG.warn("Skip malformed passkey row. user={}, credentialIdLen={}, userHandleLen={}, publicKeyLen={}",
                    credential.getUserName(),
                    safeLength(credential.getCredentialId()),
                    safeLength(credential.getUserHandle()),
                    safeLength(credential.getPublicKey()));
            return Optional.empty();
        }
        return Optional.of(
                RegisteredCredential.builder()
                        .credentialId(credentialId.get())
                        .userHandle(userHandle.get())
                        .publicKeyCose(publicKey.get())
                        .build()
        );
    }

    private Set<String> encodeCandidates(ByteArray value) {
        Set<String> candidates = new LinkedHashSet<>();
        if (value == null) {
            return candidates;
        }
        String base64 = value.getBase64();
        if (base64 != null && !base64.isBlank()) {
            candidates.add(base64);
            String base64url = base64.replace('+', '-').replace('/', '_').replaceAll("=+$", "");
            candidates.add(base64url);
        }
        return candidates;
    }

    private Optional<ByteArray> parseStoredByteArray(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ByteArray.fromBase64(encoded));
        } catch (IllegalArgumentException ignore) {
            String normalized = encoded.trim()
                    .replace('-', '+')
                    .replace('_', '/');
            int paddingLength = (4 - normalized.length() % 4) % 4;
            StringBuilder padded = new StringBuilder(normalized);
            for (int i = 0; i < paddingLength; i += 1) {
                padded.append('=');
            }
            try {
                return Optional.of(ByteArray.fromBase64(padded.toString()));
            } catch (IllegalArgumentException exception) {
                LOG.warn("Invalid passkey credential encoding, skip value. sample={}...",
                        encoded.substring(0, Math.min(8, encoded.length())).toLowerCase(Locale.ROOT));
                return Optional.empty();
            }
        }
    }

    private Integer safeLength(String value) {
        return value == null ? null : value.length();
    }
}
