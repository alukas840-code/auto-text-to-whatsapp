package com.dauren.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseInitializer {
    private final JavaPlugin plugin;
    private final Path dbPath;

    public DatabaseInitializer(JavaPlugin plugin, Path dbPath) {
        this.plugin = plugin;
        this.dbPath = dbPath;
    }

    public String jdbcUrl() {
        return "jdbc:sqlite:" + dbPath.toAbsolutePath();
    }

    public void initSchema() throws SQLException, IOException {
        Files.createDirectories(dbPath.getParent());
        String schema = new String(plugin.getResource("schema.sql").readAllBytes());
        try (Connection connection = DriverManager.getConnection(jdbcUrl());
             Statement statement = connection.createStatement()) {
            for (String chunk : schema.split(";\\n")) {
                String sql = chunk.trim();
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        }
    }
}
