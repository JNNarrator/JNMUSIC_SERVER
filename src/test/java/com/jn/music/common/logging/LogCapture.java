package com.jn.music.common.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.Appender;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.List;
import org.slf4j.LoggerFactory;

public final class LogCapture implements AutoCloseable {

    private final Appender<ILoggingEvent> appender;
    private final List<ILoggingEvent> events;

    private LogCapture(Logger logger, Appender<ILoggingEvent> appender, List<ILoggingEvent> events) {
        this.appender = appender;
        this.events = events;
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
    }

    public static LogCapture create(String loggerName) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerName);
        List<ILoggingEvent> captured = new ArrayList<>();
        AppenderBase<ILoggingEvent> appender = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent event) {
                captured.add(event);
            }
        };
        return new LogCapture(logger, appender, captured);
    }

    public List<ILoggingEvent> events() {
        return events;
    }

    @Override
    public void close() {
        appender.stop();
        Logger logger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        logger.detachAppender(appender);
    }
}
