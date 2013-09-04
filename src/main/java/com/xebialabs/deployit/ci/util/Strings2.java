package com.xebialabs.deployit.ci.util;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

import static com.google.common.base.Preconditions.checkArgument;

public class Strings2 {
    private static final char QUERY_STRING_ATTRIBUTES_SEPARATOR = '&';
    // used in regex
    private static final String QUERY_STRING_ATTRIBUTE_KEY_VALUE_SEPARATOR = Pattern.quote("=");

    private static final char COMMA_SEPARATOR = ',';

    private static final String QUOTE_CHARACTER = "\"";

    public static Map<String, String> uriQueryStringToMap(String queryString) {
        final Builder<String, String> inProgress = ImmutableMap.builder();
        for (String keyValue : Splitter.on(QUERY_STRING_ATTRIBUTES_SEPARATOR).split(queryString)) {
            String[] keyAndValue = keyValue.split(QUERY_STRING_ATTRIBUTE_KEY_VALUE_SEPARATOR);
            checkArgument(keyAndValue.length == 2, "Invalid query string format. Expected 'key=value&key2=value&...' but found section '%s'",
                    keyValue);
            inProgress.put(keyAndValue[0], keyAndValue[1]);
        }
        return inProgress.build();
    }

    public static List<String> commaSeparatedListToList(String commaSeparatedList) {
        return ImmutableList.copyOf(Splitter.on(COMMA_SEPARATOR).trimResults().split(commaSeparatedList));
    }

    public static String stripEnclosingQuotes(String value) {
        return (value.length() > 1 && value.startsWith(QUOTE_CHARACTER) && value.endsWith(QUOTE_CHARACTER))
               ? value.substring(1, value.length() - 1) : value;
    }
}
