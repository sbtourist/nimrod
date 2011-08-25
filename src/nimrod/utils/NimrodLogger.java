package nimrod.utils;

import com.google.common.base.Joiner;
import java.util.Date;
import java.util.concurrent.ConcurrentMap;
import com.google.common.collect.MapMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 */
public class NimrodLogger {

    private static final String NIMROD = "nimrod";
    private static final String ALERT = "alert";
    private static final String GAUGE = "gauge";
    private static final String COUNTER = "counter";
    private static final String TIMER = "timer";
    private static final String START_TIMER = "start";
    private static final String STOP_TIMER = "stop";
    private static final String TEMPLATE_WITH_TAGS = "[{}][{}][{}][{}][{}][{}]";
    private static final String TEMPLATE_WITH_NO_TAGS = "[{}][{}][{}][{}][{}]";
    //
    private static final ConcurrentMap<String, NimrodLogger> LOGGERS = new MapMaker().softValues().<String, NimrodLogger>makeMap();
    //
    private final Logger delegate;
    private final String logName;
    private final String metricType;
    private final String metricName;

    public static NimrodLogger forAlert(String name) {
        return forMetric(ALERT, name);
    }

    public static NimrodLogger forGauge(String name) {
        return forMetric(GAUGE, name);
    }

    public static NimrodLogger forCounter(String name) {
        return forMetric(COUNTER, name);
    }

    public static NimrodLogger forTimer(String name) {
        return forMetric(TIMER, name);
    }

    public static String prefixWith(String prefix, String name) {
        return prefix + "." + name;
    }

    public static String suffixWith(String name, String suffix) {
        return name + "." + suffix;
    }

    public static String prefixWithThreadId(String name) {
        return (Thread.currentThread().getName().hashCode() & Integer.MAX_VALUE) + "." + name;
    }

    public static String suffixWithThreadId(String name) {
        return name + "." + (Thread.currentThread().getName().hashCode() & Integer.MAX_VALUE);
    }

    public static String start() {
        return START_TIMER;
    }

    public static String stop() {
        return STOP_TIMER;
    }

    private static NimrodLogger forMetric(String type, String name) {
        String key = type + "." + name;
        NimrodLogger logger = LOGGERS.get(key);
        if (logger == null) {
            logger = new NimrodLogger(type, name);
            LOGGERS.put(key, logger);
        }
        return logger;
    }

    private NimrodLogger(String metricType, String metricName) {
        this.metricType = metricType;
        this.metricName = metricName;
        this.logName = NIMROD + "." + metricType + "." + metricName;
        this.delegate = LoggerFactory.getLogger(logName);
    }

    public String getName() {
        return delegate.getName();
    }

    public boolean isDebugEnabled() {
        return delegate.isDebugEnabled();
    }

    public void debug(String value) {
        delegate.debug(TEMPLATE_WITH_NO_TAGS, new Object[]{NIMROD, new Date().getTime(), metricType, metricName, value});
    }

    public void debug(String value, String... tags) {
        delegate.debug(TEMPLATE_WITH_TAGS, new Object[]{NIMROD, new Date().getTime(), metricType, metricName, value, Joiner.on(",").skipNulls().join(tags)});
    }

    public boolean isInfoEnabled() {
        return delegate.isInfoEnabled();
    }

    public void info(String value) {
        delegate.info(TEMPLATE_WITH_NO_TAGS, new Object[]{NIMROD, new Date().getTime(), metricType, metricName, value});
    }

    public void info(String value, String... tags) {
        delegate.info(TEMPLATE_WITH_TAGS, new Object[]{NIMROD, new Date().getTime(), metricType, metricName, value, Joiner.on(",").skipNulls().join(tags)});
    }

    public boolean isWarnEnabled() {
        return delegate.isWarnEnabled();
    }

    public void warn(String value) {
        delegate.warn(TEMPLATE_WITH_NO_TAGS, new Object[]{NIMROD, new Date().getTime(), metricType, metricName, value});
    }

    public void warn(String value, String... tags) {
        delegate.warn(TEMPLATE_WITH_TAGS, new Object[]{NIMROD, new Date().getTime(), metricType, metricName, value, Joiner.on(",").skipNulls().join(tags)});
    }

    public boolean isErrorEnabled() {
        return delegate.isErrorEnabled();
    }

    public void error(String value) {
        delegate.error(TEMPLATE_WITH_NO_TAGS, new Object[]{NIMROD, new Date().getTime(), metricType, metricName, value});
    }

    public void error(String value, String... tags) {
        delegate.error(TEMPLATE_WITH_TAGS, new Object[]{NIMROD, new Date().getTime(), metricType, metricName, value, Joiner.on(",").skipNulls().join(tags)});
    }
}
