package org.guohai.javasqlweb.beans.webauth;

import lombok.Getter;

public enum COSEAlgorithmIdentifier {
    EdDSA(-8),
    ES256(-7),
    ES384(-35),
    ES512(-36),
    RS256(-257),
    RS384(-258),
    RS512(-259),
    RS1(-65535);

    @Getter
    private final long id;

    COSEAlgorithmIdentifier(long id) {
        this.id = id;
    }
}
