package com.networth.networthbackend.common;

import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.stream.Collectors;

@UtilityClass
public class SqlBuilder {

    public static String columns(String... cols) {
        return String.join(Constants.SQL.COMMA_SPACE, cols);
    }

    public static String selectByUnique(String table, String[] columns, String uniqueCol) {
        return Constants.SQL.SELECT + String.join(Constants.SQL.COMMA_SPACE, columns) + Constants.SQL.FROM + table + Constants.SQL.WHERE + uniqueCol + Constants.SQL.EQ_PARAM + Constants.SQL.LIMIT_1;
    }

    public static String insertIgnore(String table, String[] cols) {
        String colList = Constants.SQL.OPEN_PAREN + String.join(Constants.SQL.COMMA_SPACE, cols) + Constants.SQL.CLOSE_PAREN;
        String placeholders = Constants.SQL.OPEN_PAREN + Arrays.stream(cols).map(c -> Constants.SQL.PARAM).collect(Collectors.joining(Constants.SQL.COMMA_SPACE)) + Constants.SQL.CLOSE_PAREN;
        return Constants.SQL.INSERT_IGNORE_INTO + table + Constants.SQL.SPACE + colList + Constants.SQL.VALUES + placeholders;
    }
}
