package org.guohai.javasqlweb.beans.webauth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
@AllArgsConstructor
public class RegistrationExtensionInputs {


    private final Boolean credProps;
}
