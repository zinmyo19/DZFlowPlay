package com.zynelabs.flowplay.data;

import java.util.HashMap;
import java.util.Map;

/** Decides when an account's cached data should be refreshed. */
public final class AutoRefreshPolicy {
    private static final long MINUTE = 60_000L;
    private static final long RETRY_MILLIS = MINUTE;
    private static final long PROVIDER_DEBOUNCE_MILLIS = 30_000L;
    private static final long SUCCESS_FRESH_MILLIS = 15L * MINUTE;

    private static final class AccountState {
        boolean attempted;
        boolean inFlight;
        boolean lastAttemptSuccessful;
        long lastAttemptMillis = Long.MIN_VALUE;
        long lastSuccessMillis = Long.MIN_VALUE;
        long greatestObservedMillis = Long.MIN_VALUE;
    }

    private final Map<String, AccountState> accounts = new HashMap<String, AccountState>();

    /** Returns whether an automatic refresh should be started for {@code id}. */
    public synchronized boolean shouldRefresh(String id, long nowMillis,
                                               boolean providerChanged,
                                               boolean needsRefresh) {
        if (id == null || id.length() == 0) return false;
        AccountState state = accounts.get(id);
        if (state == null) return true; // First launch: do not trust an existing cache.
        if (state.inFlight) return false;

        // A backwards clock is deliberately fail-closed.  In particular, do not turn
        // a clock correction into a burst of retries.
        if (nowMillis < state.greatestObservedMillis) return false;
        state.greatestObservedMillis = nowMillis;

        if (!state.attempted) return true;
        if (providerChanged) return elapsedAtLeast(nowMillis, state.lastAttemptMillis,
                                                   PROVIDER_DEBOUNCE_MILLIS);
        if (needsRefresh) return elapsedAtLeast(nowMillis, state.lastAttemptMillis,
                                                 RETRY_MILLIS);
        if (state.lastAttemptSuccessful) {
            return elapsedAtLeast(nowMillis, state.lastSuccessMillis, SUCCESS_FRESH_MILLIS);
        }
        return elapsedAtLeast(nowMillis, state.lastAttemptMillis, RETRY_MILLIS);
    }

    /** Claims an account refresh.  A manual claim intentionally bypasses auto cooldowns. */
    public synchronized boolean begin(String id, long nowMillis) {
        if (id == null || id.length() == 0) return false;
        AccountState state = accounts.get(id);
        if (state == null) {
            state = new AccountState();
            accounts.put(id, state);
        }
        if (state.inFlight) return false;
        state.inFlight = true;
        state.attempted = true;
        // Preserve the greatest timestamp so a regressed clock cannot make the next
        // automatic decision look older than the attempt really was.
        if (nowMillis > state.lastAttemptMillis) state.lastAttemptMillis = nowMillis;
        if (nowMillis > state.greatestObservedMillis) state.greatestObservedMillis = nowMillis;
        return true;
    }

    /** Completes a claimed refresh and records only successful completion as freshness. */
    public synchronized void finish(String id, boolean successful, long nowMillis) {
        if (id == null || id.length() == 0) return;
        AccountState state = accounts.get(id);
        if (state == null) return;
        state.inFlight = false;
        state.lastAttemptSuccessful = successful;
        if (nowMillis > state.greatestObservedMillis) state.greatestObservedMillis = nowMillis;
        if (successful && nowMillis >= state.lastSuccessMillis) {
            state.lastSuccessMillis = nowMillis;
        }
    }

    private static boolean elapsedAtLeast(long now, long then, long duration) {
        return now >= then && now - then >= duration;
    }
}
