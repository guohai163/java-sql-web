package org.guohai.javasqlweb.beans.webauth;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;

@Slf4j
@Value
@Builder(toBuilder = true)
public class PublicKeyCredentialCreationOptions {


    /**
     * 是服务器发送的一个防重放的安全随机数，建议20～32字节； <a
     * href="https://www.w3.org/TR/2021/REC-webauthn-2-20210408/#sctn-cryptographic-challenges">§13.1
     * Cryptographic Challenges</a> security consideration.
     */
    @NonNull
    private final String challenge;


    /**
     * 是Relying Party的缩写，代表网站本身，name是网站名字，id是网站域名（不带端口号）；
     *
     * <p>Its value's {@link RelyingPartyIdentity#getId() id} member specifies the <a
     * href="https://www.w3.org/TR/2021/REC-webauthn-2-20210408/#rp-id">RP ID</a> the credential
     * should be scoped to. If omitted, its value will be set by the client. See {@link
     * RelyingPartyIdentity} for further details.
     */
    @NonNull
    private final RelyingPartyIdentity rp;

    /**
     * 是服务器返回的当前用户信息，id是用户在该网站的唯一ID，name和displayName都是用户名字，仅用于显示；
     */
    @NonNull
    private final UserIdentity user;



    /**
     * 是服务器支持的非对称签名算法，最常用的算法是-7，表示用ECDSA/SHA-256签名；
     *
     * <p>The sequence is ordered from most preferred to least preferred. The client makes a
     * best-effort to create the most preferred credential that it can.
     */
    @NonNull
    private final List<PublicKeyCredentialParameters> pubKeyCredParams;

    /**
     * 是Challenge的有效时间，一般设定为60秒；
     */
    private final Long timeout;

    /**
     * Intended for use by Relying Parties that wish to express their preference for attestation
     * conveyance. The default is {@link AttestationConveyancePreference#NONE}.
     */
    @NonNull
    private final AttestationConveyancePreference attestation;

    /**
     * Intended for use by Relying Parties that wish to limit the creation of multiple credentials for
     * the same account on a single authenticator. The client is requested to return an error if the
     * new credential would be created on an authenticator that also contains one of the credentials
     * enumerated in this parameter.
     */
    private final Set<PublicKeyCredentialDescriptor> excludeCredentials;

    /**
     * 指示验证身份时，允许的Passkey来源，指定platform表示当前系统本身，还可以指定允许使用USB Key、NFC等外部验证。
     */
    private final AuthenticatorSelectionCriteria authenticatorSelection;



    /**
     * Additional parameters requesting additional processing by the client and authenticator.
     *
     * <p>For example, the caller may request that only authenticators with certain capabilities be
     * used to create the credential, or that particular information be returned in the attestation
     * object. Some extensions are defined in <a
     * href="https://www.w3.org/TR/2021/REC-webauthn-2-20210408/#sctn-extensions">§9 WebAuthn
     * Extensions</a>; consult the IANA "WebAuthn Extension Identifier" registry established by <a
     * href="https://tools.ietf.org/html/draft-hodges-webauthn-registries">[WebAuthn-Registries]</a>
     * for an up-to-date list of registered WebAuthn Extensions.
     */
    @NonNull
    private final RegistrationExtensionInputs extensions;

    @Builder
    private PublicKeyCredentialCreationOptions(
            @NonNull RelyingPartyIdentity rp,
            @NonNull UserIdentity user,
            @NonNull String challenge,
            @NonNull
            List<PublicKeyCredentialParameters> pubKeyCredParams,
            Long timeout,
            Set<PublicKeyCredentialDescriptor> excludeCredentials,
            AuthenticatorSelectionCriteria authenticatorSelection,
            AttestationConveyancePreference attestation,
            RegistrationExtensionInputs extensions) {
        this.rp = rp;
        this.user = user;
        this.challenge = challenge;
        this.pubKeyCredParams = pubKeyCredParams == null?null:pubKeyCredParams;
        this.timeout = timeout;
        this.excludeCredentials = excludeCredentials;
        this.authenticatorSelection = authenticatorSelection;
        this.attestation = attestation == null ? AttestationConveyancePreference.NONE : attestation;
        this.extensions =
                extensions == null ? RegistrationExtensionInputs.builder().build() : extensions;
    }



}
