package com.dauren;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages user profiles including surname and role.  Profiles are
 * keyed by user identifier (for example WhatsApp ID).  The manager
 * does not persist data; callers must save/restore state externally
 * if persistence is required.
 */
public class ProfileManager {
    private final Map<String, UserProfile> profiles = new ConcurrentHashMap<>();

    /**
     * Set the surname for a user.  Creates a new profile if needed.
     */
    public void setSurname(String userId, String surname) {
        UserProfile profile = profiles.computeIfAbsent(userId, k -> new UserProfile(k));
        profile.setSurname(surname);
    }

    /**
     * Set the role (job title) for a user.  Creates a new profile if needed.
     */
    public void setRole(String userId, String role) {
        UserProfile profile = profiles.computeIfAbsent(userId, k -> new UserProfile(k));
        profile.setRole(role);
    }

    /**
     * Set both surname and role for a user.  Creates a new profile if needed.
     */
    public void setProfile(String userId, String surname, String role) {
        UserProfile profile = profiles.computeIfAbsent(userId, k -> new UserProfile(k));
        profile.setSurname(surname);
        profile.setRole(role);
    }

    /**
     * Retrieve a profile for a user, or null if none exists.
     */
    public UserProfile getProfile(String userId) {
        return profiles.get(userId);
    }

    /**
     * Get all stored profiles.  Returns a collection view of
     * profiles; modifications to it will affect the internal map.
     */
    public Collection<UserProfile> getAllProfiles() {
        return profiles.values();
    }
}