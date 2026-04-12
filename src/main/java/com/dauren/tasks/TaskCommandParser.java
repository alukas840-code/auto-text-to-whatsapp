package com.dauren.tasks;

import com.dauren.model.TaskSpec;
import com.dauren.model.TaskType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class TaskCommandParser {
    private static final Pattern TIME = Pattern.compile("^([01]\\d|2[0-3]):([0-5]\\d)$");
    private static final Pattern DATE = Pattern.compile("^\\d{2}\\.\\d{2}(\\.\\d{4})?$");
    private static final List<String> WEEKDAYS = List.of("понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье");

    public TaskSpec parse(List<String> args, LocalDate now) {
        if (args.isEmpty()) throw new IllegalArgumentException("Пустая команда /task");
        int i = 0;
        String time = null;
        if (TIME.matcher(args.get(i)).matches()) {
            time = args.get(i++);
        }

        List<String> assignees = new ArrayList<>();
        while (i < args.size() && args.get(i).startsWith("@")) {
            assignees.add(args.get(i).substring(1));
            i++;
        }
        if (assignees.isEmpty()) throw new IllegalArgumentException("Укажите хотя бы одного @user");

        TaskType type = TaskType.SIMPLE;
        String dueDate = null;
        String weekday = null;

        if (i < args.size()) {
            String token = args.get(i).toLowerCase(Locale.ROOT);
            if (WEEKDAYS.contains(token)) {
                type = TaskType.WEEKLY;
                weekday = token;
                i++;
            } else if (DATE.matcher(token).matches()) {
                type = TaskType.DATED;
                dueDate = normalizeDate(token, now.getYear());
                i++;
            }
        }

        String text = String.join(" ", args.subList(i, args.size())).trim();
        if (text.isBlank()) throw new IllegalArgumentException("Не указан текст задачи");

        return new TaskSpec(type, time, assignees, dueDate, weekday, text);
    }

    private String normalizeDate(String token, int currentYear) {
        String[] p = token.split("\\.");
        int dd = Integer.parseInt(p[0]);
        int mm = Integer.parseInt(p[1]);
        int yy = p.length == 3 ? Integer.parseInt(p[2]) : currentYear;
        LocalDate d = LocalDate.of(yy, mm, dd);
        return String.format("%02d.%02d.%04d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }
}
