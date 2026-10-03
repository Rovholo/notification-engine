package com.bitkulcha.notification_engine.security;

import com.bitkulcha.notification_engine.domain.model.Role;

import java.util.Set;
import java.util.UUID;

public record AccessToken(UUID userId, Set<Role> roles) {
}
