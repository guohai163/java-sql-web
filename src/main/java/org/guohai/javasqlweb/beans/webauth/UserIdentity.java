package org.guohai.javasqlweb.beans.webauth;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class UserIdentity {

    @NonNull
    @Getter(onMethod = @__({@Override}))
    private final String name;

    @NonNull
    private final String displayName;
    @NonNull
    private final String id;

    private UserIdentity(
            @NonNull String name,
            @NonNull String displayName,
            @NonNull  String id) {
        this.name = name;
        this.displayName = displayName;
        this.id = id;
    }
}
