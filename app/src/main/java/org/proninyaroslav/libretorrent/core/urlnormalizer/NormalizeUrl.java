/*
 * Copyright (C) 2019-2020 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.proninyaroslav.libretorrent.core.urlnormalizer;

import com.anthonynsimon.url.URL;
import com.anthonynsimon.url.exceptions.MalformedURLException;

import org.proninyaroslav.libretorrent.core.exception.NormalizeUrlException;

import java.net.IDN;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class NormalizeUrl
{
    private static final HashMap<String, Integer> DEFAULT_PORT_LIST = new HashMap<>();
    static {
        DEFAULT_PORT_LIST.put("http", 80);
        DEFAULT_PORT_LIST.put("https", 443);
        DEFAULT_PORT_LIST.put("ftp", 21);
    }

    public static class Options
    {
        /**
         * Adds {@link Options#defaultProtocol} to the URL if it's protocol-relative.
         * Default is true.
         *
         * Example:
         *    Before: "//example.org"
         *    After: "http://example.org"
         */
        public boolean normalizeProtocol = true;
        public String defaultProtocol = "http";
        /**
         * Removes "www." from the URL.
         * Default is true.
         *
         * Example:
         *    Before: "http://www.example.org"
         *    After: "http://example.org"
         */
        public boolean removeWWW = true;
        /**
         * Removes query parameters that match any of the provided strings or regular expressions.
         * Default is "utm_\w+"
         *
         * Example:
         *    Before: "http://example.org?foo=bar&utm_medium=test"
         *    After: "http://example.org?foo=bar"
         */
        public String[] removeQueryParameters = new String[]{"utm_\\w+"};
        /**
         * Removes trailing slash.
         * Default is true.
         *
         * Example:
         *    Before: "http://example.org/"
         *    After: "http://example.org"
         */
        public boolean removeTrailingSlash = true;
        /**
         * Removes the default directory index file from a path that
         * matches any of the provided strings or regular expressions.
         * Default is empty.
         *
         * Example:
         *    Before: "http://example.org/index.html"
         *    After: "http://example.org"
         */
        public String[] removeDirectoryIndex = new String[]{};
        /**
         * Remove the authentication part of a URL,
         * see https://en.wikipedia.org/wiki/Basic_access_authentication
         * Default is true.
         *
         * Example:
         *    Before: "http://user:password@example.org"
         *    After: "http://example.org"
         */
        public boolean removeAuthentication = true;
        /**
         * Sorts the query parameters alphabetically by key.
         * Default is true.
         *
         * Example:
         *    Before: "http://example.org?b=two&a=one&c=three"
         *    After: "http://example.org?a=one&b=two&c=three"
         */
        public boolean sortQueryParameters = true;
        /**
         * Removes hash from the URL.
         * Default is false.
         *
         * Example:
         *    Before: "http://example.org/index.html#test"
         *    After: "http://example.org/index.html"
         */
        public boolean removeHash = false;
        /**
         * Removes HTTP(S) protocol from an URL.
         * Default is false.
         *
         * Example:
         *    Before: "http://example.org"
         *    After: "example.org"
         */
        public boolean removeProtocol = false;
        /**
         * Normalizes "https" URLs to "http".
         * Default is false.
         *
         * Example:
         *    Before: "https://example.org"
         *    After: "http://example.org"
         */
        public boolean forceHttp = false;
        /**
         * Normalizes "http" URLs to "https".
         * This option can't be used with {@link Options#forceHttp} option at the same time.
         * Default is false.
         *
         * Example:
         *    Before: "http://example.org"
         *    After: "https://example.org"
         */
        public boolean forceHttps = false;
        /**
         * Decode the percent-decoded symbols and IDN in the URL to Unicode symbols.
         * Default is true.
         *
         * Example:
         *    Before: "https://example.org/?foo=bar*%7C%3C%3E%3A%22"
         *    After: "http://example.org/?foo=bar*|<>:""
         */
        public boolean decode = true;
    }

    /**
     * More about URL normalization: https://en.wikipedia.org/wiki/URL_normalization
     *
     * @param url URL
     * @return normalized URL
     */

    public static String normalize(String url) throws NormalizeUrlException
    {
        return normalize(url, null);
    }

    /**
     * More about URL normalization: https://en.wikipedia.org/wiki/URL_normalization
     *
     * @param url URL
     * @param options additional options for normalization
     * @return normalized URL
     */

    public static String normalize(String url, Options options) throws NormalizeUrlException
    {
        String normalizedUrl;
        try {
            normalizedUrl = doNormalize(url, options);

        } catch (Exception e) {
            throw new NormalizeUrlException("Cannot normalize URL", e);
        }

        return normalizedUrl;
    }

    private static String doNormalize(String url, Options options) throws MalformedURLException
    {
        if (url == null || url.isEmpty())
            return url;

        if (options == null)
            options = new Options();
        if (options.forceHttp && options.forceHttps)
            throw new IllegalStateException("The 'forceHttp' and 'forceHttps' options cannot be used together");

        url = url.trim();

        boolean hasRelativeProtocol = url.startsWith("//");
        boolean isRelativeUrl = !hasRelativeProtocol && url.matches("^.*/");
        if (!isRelativeUrl)
            url = url.replaceFirst("^(?!(?:\\w+:)?//)|^//", options.defaultProtocol + "://");

        URL urlObj = URL.parse(url);
        String protocol, hash, user, password, path, queryStr, host;
        Map<String, Collection<String>> query;

        protocol = urlObj.getScheme();
        hash = urlObj.getFragment();
        user = urlObj.getUsername();
        password = urlObj.getPassword();
        if (options.decode) {
            queryStr = (urlObj.getQuery() == null ? null : PercentEncoder.decode(urlObj.getQuery()));
            path = urlObj.getPath();
            host = IDN.toUnicode(urlObj.getHost());
        } else {
            queryStr = urlObj.getQuery();
            path = urlObj.getRawPath();
            host = urlObj.getHost();
        }
        query = parseQuery(queryStr);

        if (options.forceHttp && protocol.equals("https"))
            protocol = "http";
        if (options.forceHttps && protocol.equals("http"))
            protocol = "https";

        if (options.removeAuthentication) {
            user = null;
            password = null;
        }

        if (options.removeHash)
            hash = null;

        if (host != null) {
            /* Remove trailing dot */
            host = host.replaceFirst("\\.$", "");
            /* Ignore default ports */
            Integer port = DEFAULT_PORT_LIST.get(protocol);
            if (port != null)
                host = host.replaceFirst(":" + port + "$", "");

            if (options.removeWWW && host.matches("www\\.([a-z\\-\\d]{2,63})\\.([a-z.]{2,5})$")) {
                /*
                 * Each label should be max 63 at length (min: 2).
                 * The extension should be max 5 at length (min: 2).
                 * See: https://en.wikipedia.org/wiki/Hostname#Restrictions_on_valid_host_names
                 */
                host = host.replaceFirst("^www\\.", "");
            }
        }

        if (path != null) {
            path = PathResolver.resolve(path, path);
            /* Remove duplicate slashes if not preceded by a protocol */
            path = path.replaceAll("(?<!:)/{2,}", "/");

            if (options.removeDirectoryIndex.length > 0) {
                String[] pathComponents = path.split("/");
                if (pathComponents.length > 0) {
                    String lastComponent = pathComponents[pathComponents.length - 1];
                    if (isMatch(lastComponent, options.removeDirectoryIndex))
                        path = '/' + join("/", Arrays.copyOfRange(pathComponents, 1, pathComponents.length - 1));
                    if (!path.endsWith("/"))
                        path += '/';
                }
            }
            if (options.removeTrailingSlash)
                path = path.replaceFirst("/$", "");
        }

        if (!query.isEmpty()) {
            if (options.removeQueryParameters.length > 0) {
                Iterator<String> it = query.keySet().iterator();
                while (it.hasNext()) {
                    String key = it.next();
                    if (isMatch(key, options.removeQueryParameters))
                        it.remove();
                }
            }
            if (options.sortQueryParameters)
                query = new TreeMap<>(query);
        }

        url = urlToString(protocol, user, password, host,
                path, mapToQuery(query), hash);

        /* Restore relative protocol, if applicable */
        if (hasRelativeProtocol && !options.normalizeProtocol)
            url = url.replaceFirst("^(?:https?:)?//", "//");

        /* Remove http/https */
        if (options.removeProtocol)
            url = url.replaceFirst("^(?:https?:)?//", "");

        return url;
    }

    private static boolean isMatch(String s, String[] filterList)
    {
        for (String filter : filterList) {
            if (filter == null)
                continue;

            if (s.matches(filter))
                return true;
        }

        return false;
    }

    private static String join(CharSequence delimiter, String[] tokens)
    {
        int length = tokens.length;
        if (length == 0)
            return "";

        StringBuilder sb = new StringBuilder();
        sb.append(tokens[0]);
        for (int i = 1; i < length; i++) {
            sb.append(delimiter);
            sb.append(tokens[i]);
        }

        return sb.toString();
    }

    private static Map<String, Collection<String>> parseQuery(String query)
    {
        HashMap<String, Collection<String>> queryPairs = new HashMap<>();

        if (query == null || query.isEmpty() || query.equals("?"))
            return queryPairs;

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            /* Ignore nested query string in the value of the pair, if one exists */
            int equalPos = pair.indexOf('=');
            if (equalPos <= 0)
                continue;

            String key = pair.substring(0, equalPos);
            String value = pair.substring(equalPos + 1);

            if (!key.isEmpty()) {
                Collection<String> existing = queryPairs.get(key);
                if (existing == null)
                    existing = new ArrayList<>();

                if (!value.isEmpty())
                    existing.add(value);
                queryPairs.put(key, existing);
            }
        }

        return queryPairs;
    }

    private static String mapToQuery(Map<String, Collection<String>> query)
    {
        if (query.isEmpty())
            return null;

        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, Collection<String>> parameter : query.entrySet()) {
            for (String value : parameter.getValue()) {
                if (i > 0)
                    sb.append("&");
                sb.append(parameter.getKey());
                if (value != null && !value.isEmpty()) {
                    sb.append("=");
                    sb.append(value);
                }
                i++;
            }
        }

        return sb.toString();
    }

    private static String urlToString(String protocol, String user, String password,
                                      String host, String path,
                                      String query, String hash)
    {
        StringBuilder output = new StringBuilder();

        output.append(protocol).append(':');

        output.append("//");
        if (user != null && !user.isEmpty())
            output.append(makeUserInfo(user, password)).append('@');

        if (host != null)
            output.append(host);
        if (path != null && !path.isEmpty())
            output.append(path);
        else if (query != null && !query.isEmpty() || hash != null && !hash.isEmpty())
            /* Separate by slash if has query or hash and path is empty */
            output.append("/");

        if (query != null && !query.isEmpty())
            output.append('?').append(query);

        if (hash != null && !hash.isEmpty())
            output.append('#').append(hash);

        return output.toString();
    }

    private static String makeUserInfo(String user, String password)
    {
        if (password == null)
            return user;

        return String.format("%s:%s", user, password);
    }
}
