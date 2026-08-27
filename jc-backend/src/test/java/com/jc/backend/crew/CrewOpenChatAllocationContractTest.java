package com.jc.backend.crew;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CrewOpenChatAllocationContractTest {

    private static final String DOCUMENT =
            "docs/platform/governance/SC-PF6-CREW-OPEN-CHAT-ALLOCATION.md";

    @Test
    void allocationLocksDisclosureSqlAndAuthorityBoundaries() throws IOException {
        String allocation = read(DOCUMENT);

        for (String required : new String[] {
                "sc-pf6-crew-open-chat-allocation-v1",
                "APPROVED / IMPLEMENTATION_AUTHORITY_GRANTED",
                "CONTRACT_ID=crew-open-chat-access-v1",
                "WRITE_AUTHORITY=CREW_OWNER_ONLY",
                "READ_AUTHORITY=OWNER_OR_APPROVED_ONLY",
                "ANONYMOUS_DISCLOSURE=NO",
                "PENDING_DISCLOSURE=NO",
                "SERVER_SIDE_FETCH=NO",
                "63_crew_open_chat.sql",
                "64_crew_open_chat_smoke_test.sql",
                "SQL `65+` remains unallocated",
                "crew_recommendation_exposure_v1",
                "open_chat_url VARCHAR(500)",
                "RECOMMENDATION_CREW_COLUMNS=id,owner_id,region_id,travel_date,capacity,recruiting,created_at",
                "revoke table-level `SELECT` on `public.crews` from `jc_recommendation`",
                "`jc_recommendation` cannot select `open_chat_url`"
        }) {
            assertTrue(allocation.contains(required), "PF6 allocation missing: " + required);
        }
    }

    @Test
    void allocationDoesNotGrantMessagingRankingOrUnsafeUrlAuthority() throws IOException {
        String allocation = read(DOCUMENT);

        assertTrue(allocation.contains("does not implement chat, messaging, presence, WebSocket, SSE"));
        assertTrue(allocation.contains("`open_chat_url` is presentation-only gated data and is not a ranking feature"));
        assertTrue(allocation.contains("Recommendation code must not select, map, rank, filter, log or expose `open_chat_url`"));
        assertTrue(allocation.contains("accepting `http`, scheme-relative, hostless, or user-info URLs"));
        assertTrue(allocation.contains("server-side HTTP requests to the configured URL"));
        assertTrue(allocation.contains("deployment or production activation"));
        assertFalse(allocation.contains("READ_AUTHORITY=PUBLIC"));
        assertFalse(allocation.contains("SERVER_SIDE_FETCH=YES"));
        assertFalse(allocation.contains("SQL `65+` allocated"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(repositoryRoot().resolve(relativePath))
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("docs/platform/governance"))
                    && Files.isRegularFile(candidate.resolve("jc-backend/build.gradle.kts"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("repository root not found from " + current);
    }
}
