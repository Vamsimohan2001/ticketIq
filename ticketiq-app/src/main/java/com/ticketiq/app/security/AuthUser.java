package com.ticketiq.app.security;

import com.ticketiq.core.model.Role;

/** Who is calling, taken only from a verified token (never from the request body). */
public record AuthUser(long id, String userId, Role role) {

    public boolean isStaff() {
        return role == Role.AGENT || role == Role.ADMIN;
    }
}
