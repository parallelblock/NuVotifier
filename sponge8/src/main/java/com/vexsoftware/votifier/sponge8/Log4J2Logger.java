package com.vexsoftware.votifier.sponge8;

import com.vexsoftware.votifier.platform.LoggingAdapter;
import org.apache.logging.log4j.Logger;

public class Log4J2Logger implements LoggingAdapter {

    private final Logger l;
    public Log4J2Logger(Logger l) {
        this.l = l;
    }

    @Override
    public void error(String s) {
        l.error(s);
    }

    @Override
    public void error(String s, Object... o) {
        l.error(s, o);
    }

    @Override
    public void warn(String s) {
        l.warn(s);
    }

    @Override
    public void warn(String s, Object... o) {
        l.warn(s, o);
    }

    @Override
    public void info(String s) {
        l.info(s);
    }

    @Override
    public void info(String s, Object... o) {
        l.info(s, o);
    }
}
