package com.dauren;

/**
 * Represents a user's profile (for example, a group participant).  It
 * stores the surname and role (job title) used in list and who
 * commands.  The userId identifies the user in the bridge (e.g.
 * WhatsApp ID) and is immutable.
 */
public class UserProfile {
    private final String userId;
    private String surname;
    private String role;

    public UserProfile(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}