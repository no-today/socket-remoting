package io.github.no.today.socket.remoting.core.supper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author no-today
 * @date 2024/02/27 16:42
 */
public class LogUtil {

    private static Level level = Level.INFO;

    public enum Level {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    private final static DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    public static void setLevel(Level level) {
        LogUtil.level = level;
    }

    private static void println(String level, String format, Object[] args) {
        format = format.replace("{}", "%s");
        System.out.printf(LocalDateTime.now().format(DATE_TIME_FORMATTER) + " " + level + " [Socket] " + format + "%n", args);
    }

    public static void debug(String format, Object... args) {
        if (Level.DEBUG.ordinal() == level.ordinal()) {
            println("DEBUG", format, args);
        }
    }

    public static void info(String format, Object... args) {
        if (Level.INFO.ordinal() >= level.ordinal()) {
            println("INFO ", format, args);
        }
    }

    public static void warn(String format, Object... args) {
        if (Level.WARN.ordinal() >= level.ordinal()) {
            println("WARN ", format, args);
        }
    }

    public static void error(String format, Object... args) {
        if (Level.ERROR.ordinal() >= level.ordinal()) {
            println("ERROR", format, args);
        }
    }
}
