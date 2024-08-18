package org.guohai.javasqlweb.beans.webauth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
@AllArgsConstructor
public class AuthenticatorSelectionCriteria {

    private final String authenticatorAttachment;

    private final String residentKey;

    private final Boolean requireResidentKey;
}
