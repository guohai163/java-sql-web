package org.guohai.javasqlweb.beans.webauth;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

@AllArgsConstructor
public enum PublicKeyCredentialType {
    PUBLIC_KEY("public-key");

    @Getter
    @NonNull
    private final String id;
}
