package org.guohai.javasqlweb.beans.webauth;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;

/**
 * Used to supply additional Relying Party attributes when creating a new credential.
 *
 * @see <a
 *     href="https://www.w3.org/TR/2021/REC-webauthn-2-20210408/#dictdef-publickeycredentialrpentity">§5.4.2.
 *     Relying Party Parameters for Credential Generation (dictionary PublicKeyCredentialRpEntity)
 *     </a>
 */
@Value
@Builder(toBuilder = true)
public class RelyingPartyIdentity implements PublicKeyCredentialEntity {

    /**
     * The human-palatable name of the Relaying Party.
     *
     * <p>For example: "ACME Corporation", "Wonderful Widgets, Inc." or "ОАО Примертех".
     */
    @NonNull
    @Getter(onMethod = @__({@Override}))
    private final String name;

    /**
     * A unique identifier for the Relying Party, which sets the <a
     * href="https://www.w3.org/TR/2021/REC-webauthn-2-20210408/#rp-id">RP ID</a>.
     *
     * <p>This defines the domains where users' credentials are valid. See <a
     * href="https://www.w3.org/TR/2021/REC-webauthn-2-20210408/#scope">RP ID: scope</a> for details
     * and examples.
     *
     * @see <a href="https://www.w3.org/TR/2021/REC-webauthn-2-20210408/#rp-id">RP ID</a>
     * @see <a href="https://www.w3.org/TR/2021/REC-webauthn-2-20210408/#scope">RP ID: scope</a>
     */
    @NonNull private final String id;


    private RelyingPartyIdentity(
            @NonNull String name, @NonNull  String id) {
        this.name = name;
        this.id = id;
    }


}