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
        return Constants.SQL.SELECT + String.join(Constants.SQL.COMMA_SPACE, columns)
                + Constants.SQL.FROM + table
                + Constants.SQL.WHERE + uniqueCol + Constants.SQL.EQ_PARAM
                + Constants.SQL.LIMIT_1;
    }

    public static String insertOnDuplicateUpdate(String table, String[] cols) {
        String colList = Constants.SQL.OPEN_PAREN + String.join(Constants.SQL.COMMA_SPACE, cols) + Constants.SQL.CLOSE_PAREN;
        String placeholders = Constants.SQL.OPEN_PAREN + Arrays.stream(cols).map(c -> Constants.SQL.PARAM)
                .collect(Collectors.joining(Constants.SQL.COMMA_SPACE)) + Constants.SQL.CLOSE_PAREN;
        String update = Arrays.stream(cols)
                .map(c -> c + Constants.SQL.EQ_VALUES_OPEN + c + Constants.SQL.CLOSE)
                .collect(Collectors.joining(Constants.SQL.COMMA_SPACE));
        return Constants.SQL.INSERT_INTO + table + Constants.SQL.SPACE + colList
                + Constants.SQL.VALUES + placeholders
                + Constants.SQL.ON_DUPLICATE_KEY_UPDATE + update;
    }
}
