package calculator;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;
import java.util.regex.Pattern;

public class LogFormatter extends Formatter {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final Map<Level, String> levelMsgMap = Collections.unmodifiableMap(
            new HashMap<Level, String>() {{
                put(Level.SEVERE, "ERROR");
                put(Level.WARNING, "WARN");
                put(Level.INFO, "INFO");
                put(Level.CONFIG, "CONF");
                put(Level.FINE, "FINE");
                put(Level.FINER, "FINE");
                put(Level.FINEST, "FINE");
            }});

    private AtomicInteger nameColumnWidth = new AtomicInteger(16);


    public static void applyToRoot() {
        applyToRoot(new ConsoleHandler());
    }


    public static void applyToRoot(Handler handler) {
        handler.setFormatter(new LogFormatter());
        Logger root = Logger.getLogger("");
        root.setUseParentHandlers(false);
        for (Handler h : root.getHandlers()) {
            if (h instanceof ConsoleHandler)
                root.removeHandler(h);
        }
        root.addHandler(handler);
    }


    @Override
    public String format(LogRecord record) {

        StringBuilder sb = new StringBuilder(200);

        Instant instant = Instant.ofEpochMilli(record.getMillis());
        LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        sb.append(formatter.format(ldt));
        sb.append(" ");

        sb.append(levelMsgMap.get(record.getLevel()));
        sb.append(" ");


        String category;
        if (record.getSourceClassName() != null) {
            category = record.getSourceClassName();
            if (record.getSourceMethodName() != null) {
                category += " " + record.getSourceMethodName();
            }
        } else {
            category = record.getLoggerName();
        }
        int width = nameColumnWidth.intValue();
        category = adjustLength(category, width);
        sb.append("[");
        sb.append(category);
        sb.append("] ");

        if (category.length() > width) {
            // grow in length.
            nameColumnWidth.compareAndSet(width, category.length());
        }

        sb.append(formatMessage(record));

        sb.append(System.lineSeparator());
        if (record.getThrown() != null) {
            try {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                record.getThrown().printStackTrace(pw);
                pw.close();
                sb.append(sw.toString());
            } catch (Exception ex) {
            }
        }

        return sb.toString();
    }


    static String adjustLength(String packageName, int aimLength) {

        int overflowWidth = packageName.length() - aimLength;

        String[] fragment = packageName.split(Pattern.quote("."));
        for (int i = 0; i < fragment.length - 1; i++) {
            if (fragment[i].length() > 1 && overflowWidth > 0) {

                int cutting = (fragment[i].length() - 1) - overflowWidth;
                cutting = (cutting < 0) ? (fragment[i].length() - 1) : overflowWidth;

                fragment[i] = fragment[i].substring(0, fragment[i].length() - cutting);
                overflowWidth -= cutting;
            }
        }

        String result = String.join(".", fragment);
        while (result.length() < aimLength) {
            result += " ";
        }

        return result;
    }
}