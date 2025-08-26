package com.networth.networthbackend.users;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EnsureUserResult {
    User user;
    boolean created;
}
