/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.text.translate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Class holding various entity data for HTML and XML - generally for use with
 * the LookupTranslator.
 * All Maps are generated using {@code java.util.Collections.unmodifiableMap()}.
 *
 * @since 1.0
 */
public class EntityArrays {
    /**
     * A Map&lt;CharSequence, CharSequence&gt; to escape the basic XML and HTML
     * character entities.
     *
     * Namely: {@code " & < >}
     */
    public static final Map<CharSequence, CharSequence> BASIC_ESCAPE;
    static {
        final Map<CharSequence, CharSequence> initialMap = new HashMap<CharSequence, CharSequence>();
        initialMap.put("\"", "&quot;"); // " - double-quote
        initialMap.put("&", "&amp;");   // & - ampersand
        initialMap.put("<", "&lt;");    // < - less-than
        initialMap.put(">", "&gt;");    // > - greater-than
        BASIC_ESCAPE = Collections.unmodifiableMap(initialMap);
    }

    /**
     * Reverse of {@link #BASIC_ESCAPE} for unescaping purposes.
     */
    public static final Map<CharSequence, CharSequence> BASIC_UNESCAPE;
    static {
        BASIC_UNESCAPE = Collections.unmodifiableMap(invert(BASIC_ESCAPE));
    }

    /**
     * A Map&lt;CharSequence, CharSequence&gt; to escape the apostrophe character to
     * its XML character entity.
     */
    public static final Map<CharSequence, CharSequence> APOS_ESCAPE;
    static {
        final Map<CharSequence, CharSequence> initialMap = new HashMap<CharSequence, CharSequence>();
        initialMap.put("'", "&apos;"); // XML apostrophe
        APOS_ESCAPE = Collections.unmodifiableMap(initialMap);
    }

    /**
     * Reverse of {@link #APOS_ESCAPE} for unescaping purposes.
     */
    public static final Map<CharSequence, CharSequence> APOS_UNESCAPE;
    static {
        APOS_UNESCAPE = Collections.unmodifiableMap(invert(APOS_ESCAPE));
    }

    /**
     * Used to invert an escape Map into an unescape Map.
     * @param map Map&lt;String, String&gt; to be inverted
     * @return Map&lt;String, String&gt; inverted array
     */
    public static Map<CharSequence, CharSequence> invert(final Map<CharSequence, CharSequence> map) {
        final Map<CharSequence, CharSequence> newMap = new HashMap<CharSequence, CharSequence>();
        for (final Map.Entry<CharSequence, CharSequence> pair : map.entrySet()) {
            newMap.put(pair.getValue(), pair.getKey());
        }
        return newMap;
    }
}
