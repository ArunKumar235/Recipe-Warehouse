package org.example.securin.utility;

public enum FilterOperator {
    GT(">"), GTE(">="), LT("<"), LTE("<="), EQ("=");

    FilterOperator(String symbol) {
    }

    public static FilterValue parse(String input) {
        if (input == null)
            return null;
        if (input.startsWith(">="))
            return new FilterValue(GTE, input.substring(2));
        if (input.startsWith(">"))
            return new FilterValue(GT, input.substring(1));
        if (input.startsWith("<="))
            return new FilterValue(LTE, input.substring(2));
        if (input.startsWith("<"))
            return new FilterValue(LT, input.substring(1));
        return new FilterValue(EQ, input);
    }

}
