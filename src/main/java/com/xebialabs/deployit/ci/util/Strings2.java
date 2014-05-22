/**
 * Copyright (c) 2014, XebiaLabs B.V., All rights reserved.
 *
 *
 * The XL Deploy plugin for Jenkins is licensed under the terms of the GPLv2
 * <http://www.gnu.org/licenses/old-licenses/gpl-2.0.html>, like most XebiaLabs Libraries.
 * There are special exceptions to the terms and conditions of the GPLv2 as it is applied to
 * this software, see the FLOSS License Exception
 * <https://github.com/jenkinsci/deployit-plugin/blob/master/LICENSE>.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation; version 2
 * of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation, Inc., 51 Franklin St, Fifth
 * Floor, Boston, MA 02110-1301  USA
 */

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
    private static final char COMMA_SEPARATOR = ',';

    private static final String QUOTE_CHARACTER = "\"";

    public static List<String> commaSeparatedListToList(String commaSeparatedList) {
        return ImmutableList.copyOf(Splitter.on(COMMA_SEPARATOR).trimResults().split(commaSeparatedList));
    }

    public static String stripEnclosingQuotes(String value) {
        return (value.length() > 1 && value.startsWith(QUOTE_CHARACTER) && value.endsWith(QUOTE_CHARACTER))
               ? value.substring(1, value.length() - 1) : value;
    }
}
