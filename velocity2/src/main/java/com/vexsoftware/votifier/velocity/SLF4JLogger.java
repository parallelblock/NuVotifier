package com.vexsoftware.votifier.velocity;

import com.vexsoftware.votifier.platform.LoggingAdapter;
import org.slf4j.Logger;

public class SLF4JLogger implements LoggingAdapter {

    private final Logger l;
    public SLF4JLogger(Logger l) {
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
