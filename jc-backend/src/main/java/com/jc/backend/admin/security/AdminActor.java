package com.jc.backend.admin.security;

/** Minimal DB-authoritative Admin identity returned by the shared authorization guard. */
public record AdminActor(
        long adminUserId,
        String loginId,
        String role,
        String accountStatus) {}
