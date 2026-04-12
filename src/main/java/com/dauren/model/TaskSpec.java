package com.dauren.model;

import java.util.List;

public record TaskSpec(
        TaskType type,
        String reminderTime,
        List<String> assignees,
        String dueDate,
        String weekday,
        String text
) {}
