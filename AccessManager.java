package com.dauren;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages access levels for users.  Levels are integers 1–3
 * corresponding to base, manager and owner roles.  If a user has no
 * explicit entry they default to level 1.  The manager does not
 * persist data; callers must save/restore state externally if
 * persistence is required.
 */
public class AccessManager {
    private final Map<String, Integer> levels = new ConcurrentHashMap<>();

    /**
     * Get the current access level for a user.  Returns 1 if the
     * user has no explicit level assigned.
     */
    public int getAccessLevel(String userId) {
        return levels.getOrDefault(userId, 1);
    }

    /**
     * Set a new access level for a user.  Only values 1–3 are
     * accepted; other values are ignored.
     */
    public void setAccessLevel(String userId, int level) {
        if (level < 1 || level > 3) {
            return;
        }
        levels.put(userId, level);
    }

    /**
     * Reset a user's access level to the default (1).
     */
    public void resetAccessLevel(String userId) {
        levels.remove(userId);
    }
}