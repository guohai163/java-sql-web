package org.guohai.javasqlweb.beans.webauth;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class PublicKeyCredentialParameters {

    @NonNull
    private final COSEAlgorithmIdentifier alg;

    /** Specifies the type of credential to be created. */
    @NonNull
    @Builder.Default
    private final PublicKeyCredentialType type = PublicKeyCredentialType.PUBLIC_KEY;

    private PublicKeyCredentialParameters(
            @NonNull COSEAlgorithmIdentifier alg) {
        this.alg = alg;
    }

}
