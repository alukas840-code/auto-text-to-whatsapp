package com.dauren.storage;

import com.dauren.model.TaskType;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SqliteStorage {
    private final String jdbcUrl;

    public SqliteStorage(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    public void ensureUser(String userId, String userName) {
        String now = Instant.now().toString();
        try (Connection c = connection()) {
            try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO users(id, whatsapp_name, created_at) VALUES(?,?,?)")) {
                ps.setString(1, userId);
                ps.setString(2, userName);
                ps.setString(3, now);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE users SET whatsapp_name=? WHERE id=? AND ? IS NOT NULL")) {
                ps.setString(1, userName);
                ps.setString(2, userId);
                ps.setString(3, userName);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO profiles(user_id, surname, role) VALUES(?, NULL, NULL)")) {
                ps.setString(1, userId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO access_levels(user_id, level, updated_at) VALUES(?,1,?)")) {
                ps.setString(1, userId);
                ps.setString(2, now);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void rememberGroupMember(String chatId, String userId) {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO group_members(chat_id,user_id) VALUES(?,?)")) {
            ps.setString(1, chatId);
            ps.setString(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int getAccessLevel(String userId) {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement("SELECT level FROM access_levels WHERE user_id=?")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 1;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setAccessLevel(String userId, int level) {
        String now = Instant.now().toString();
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement("INSERT INTO access_levels(user_id, level, updated_at) VALUES(?,?,?) ON CONFLICT(user_id) DO UPDATE SET level=excluded.level, updated_at=excluded.updated_at")) {
            ps.setString(1, userId);
            ps.setInt(2, level);
            ps.setString(3, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<UserProfileRow> getProfile(String userId) {
        String sql = "SELECT u.id, u.whatsapp_name, p.surname, p.role, COALESCE(a.level,1) AS access_level FROM users u LEFT JOIN profiles p ON p.user_id=u.id LEFT JOIN access_levels a ON a.user_id=u.id WHERE u.id=?";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new UserProfileRow(rs.getString("id"), rs.getString("whatsapp_name"), rs.getString("surname"), rs.getString("role"), rs.getInt("access_level")));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<UserProfileRow> listGroupProfiles(String chatId) {
        String sql = "SELECT u.id, u.whatsapp_name, p.surname, p.role, COALESCE(a.level,1) AS access_level FROM group_members gm JOIN users u ON u.id=gm.user_id LEFT JOIN profiles p ON p.user_id=u.id LEFT JOIN access_levels a ON a.user_id=u.id WHERE gm.chat_id=? ORDER BY COALESCE(p.surname,u.whatsapp_name,u.id)";
        List<UserProfileRow> rows = new ArrayList<>();
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, chatId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new UserProfileRow(rs.getString("id"), rs.getString("whatsapp_name"), rs.getString("surname"), rs.getString("role"), rs.getInt("access_level")));
                }
            }
            return rows;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setSurname(String userId, String surname) { updateProfile(userId, surname, null); }
    public void setRole(String userId, String role) { updateProfile(userId, null, role); }

    public void setProfile(String userId, String surname, String role) { updateProfile(userId, surname, role); }

    private void updateProfile(String userId, String surname, String role) {
        ensureUser(userId, null);
        try (Connection c = connection()) {
            if (surname != null) {
                try (PreparedStatement ps = c.prepareStatement("UPDATE profiles SET surname=? WHERE user_id=?")) {
                    ps.setString(1, surname);
                    ps.setString(2, userId);
                    ps.executeUpdate();
                }
            }
            if (role != null) {
                try (PreparedStatement ps = c.prepareStatement("UPDATE profiles SET role=? WHERE user_id=?")) {
                    ps.setString(1, role);
                    ps.setString(2, userId);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public long createTask(String chatId, String assigneeId, String createdBy, String text, TaskType type, String dueDate, String weekday, String remindTime) {
        String now = Instant.now().toString();
        String sql = "INSERT INTO tasks(chat_id, assignee_id, created_by, text, type, due_date, weekday, remind_time, created_at) VALUES(?,?,?,?,?,?,?,?,?)";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, chatId); ps.setString(2, assigneeId); ps.setString(3, createdBy); ps.setString(4, text);
            ps.setString(5, type.name().toLowerCase()); ps.setString(6, dueDate); ps.setString(7, weekday); ps.setString(8, remindTime); ps.setString(9, now);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1L;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<TaskRow> listActiveTasks(String chatId) {
        String sql = "SELECT * FROM tasks WHERE chat_id=? AND deleted_at IS NULL AND (done_at IS NULL OR type='weekly') ORDER BY id";
        List<TaskRow> rows = new ArrayList<>();
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, chatId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(TaskRow.from(rs));
            }
            return rows;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<TaskRow> getTask(long taskId) {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement("SELECT * FROM tasks WHERE id=?")) {
            ps.setLong(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(TaskRow.from(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void markTaskDone(long taskId, String cycleKey, String actorId) {
        String now = Instant.now().toString();
        try (Connection c = connection()) {
            try (PreparedStatement ps = c.prepareStatement("UPDATE tasks SET done_at=? WHERE id=? AND type!='weekly'")) {
                ps.setString(1, now); ps.setLong(2, taskId); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO task_events(task_id,event_type,actor_id,payload,event_at) VALUES(?,?,?,?,?)")) {
                ps.setLong(1, taskId);
                ps.setString(2, "done");
                ps.setString(3, actorId);
                ps.setString(4, "{\"cycle\":\"" + cycleKey + "\"}");
                ps.setString(5, now);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteTask(long taskId) {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement("UPDATE tasks SET deleted_at=? WHERE id=?")) {
            ps.setString(1, Instant.now().toString());
            ps.setLong(2, taskId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<TaskRow> dueTasks(String date, String time, String weekday) {
        String sql = "SELECT * FROM tasks WHERE deleted_at IS NULL AND ((type='dated' AND done_at IS NULL AND due_date=? AND remind_time<=?) OR (type='weekly' AND weekday=? AND remind_time<=?))";
        List<TaskRow> rows = new ArrayList<>();
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, date); ps.setString(2, time); ps.setString(3, weekday); ps.setString(4, time);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(TaskRow.from(rs));
            }
            return rows;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean hasEvent(long taskId, String eventType, String cycleKey) {
        String sql = "SELECT id FROM task_events WHERE task_id=? AND event_type=? AND payload LIKE ? LIMIT 1";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, taskId); ps.setString(2, eventType); ps.setString(3, "%\"cycle\":\"" + cycleKey + "\"%");
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void addTaskEvent(long taskId, String eventType, String payload) {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement("INSERT INTO task_events(task_id,event_type,payload,event_at) VALUES(?,?,?,?)")) {
            ps.setLong(1, taskId);
            ps.setString(2, eventType);
            ps.setString(3, payload);
            ps.setString(4, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveContext(String scopeType, String scopeId, String contextJson, boolean greeted) {
        String sql = "INSERT INTO conversation_contexts(scope_type,scope_id,context_json,greeted,updated_at) VALUES(?,?,?,?,?) ON CONFLICT(scope_type,scope_id) DO UPDATE SET context_json=excluded.context_json,greeted=excluded.greeted,updated_at=excluded.updated_at";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, scopeType); ps.setString(2, scopeId); ps.setString(3, contextJson); ps.setInt(4, greeted ? 1 : 0); ps.setString(5, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<ContextRow> getContext(String scopeType, String scopeId) {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement("SELECT * FROM conversation_contexts WHERE scope_type=? AND scope_id=?")) {
            ps.setString(1, scopeType); ps.setString(2, scopeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new ContextRow(rs.getString("scope_type"), rs.getString("scope_id"), rs.getString("context_json"), rs.getInt("greeted") == 1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void audit(String actorId, String action, String objectType, String objectId, String detailsJson) {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement("INSERT INTO audit_logs(actor_id,action,object_type,object_id,details,at) VALUES(?,?,?,?,?,?)")) {
            ps.setString(1, actorId); ps.setString(2, action); ps.setString(3, objectType); ps.setString(4, objectId); ps.setString(5, detailsJson); ps.setString(6, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<RateLimitRow> getRate(String key) {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement("SELECT * FROM rate_limits WHERE key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new RateLimitRow(rs.getString("key"), rs.getLong("window_start"), rs.getInt("count"), rs.getLong("cooldown_until"))) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void upsertRate(String key, long windowStart, int count, long cooldownUntil) {
        String sql = "INSERT INTO rate_limits(key,window_start,count,cooldown_until) VALUES(?,?,?,?) ON CONFLICT(key) DO UPDATE SET window_start=excluded.window_start,count=excluded.count,cooldown_until=excluded.cooldown_until";
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key); ps.setLong(2, windowStart); ps.setInt(3, count); ps.setLong(4, cooldownUntil); ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public record UserProfileRow(String id, String whatsappName, String surname, String role, int accessLevel) {}
    public record ContextRow(String scopeType, String scopeId, String contextJson, boolean greeted) {}
    public record RateLimitRow(String key, long windowStart, int count, long cooldownUntil) {}

    public record TaskRow(long id, String chatId, String assigneeId, String createdBy, String text, String type,
                          String dueDate, String weekday, String remindTime, String doneAt, String deletedAt) {
        static TaskRow from(ResultSet rs) throws SQLException {
            return new TaskRow(
                    rs.getLong("id"), rs.getString("chat_id"), rs.getString("assignee_id"), rs.getString("created_by"), rs.getString("text"), rs.getString("type"),
                    rs.getString("due_date"), rs.getString("weekday"), rs.getString("remind_time"), rs.getString("done_at"), rs.getString("deleted_at")
            );
        }

        public TaskType taskType() {
            return TaskType.valueOf(type.toUpperCase());
        }
    }
}
