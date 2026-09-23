package com.stock.analyzer.backtest.model;

public enum Direction {
    LONG("long"),
    SHORT("short");

    private final String value;

    Direction(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static Direction fromString(String s) {
        return s.equalsIgnoreCase("long") ? LONG : SHORT;
    }
}
