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

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.*;

public class NormalizeUrlTest
{
    static class TestNormalize
    {
        String name;
        String input;
        String output;
        NormalizeUrl.Options options;

        TestNormalize(String name, String input, String output, NormalizeUrl.Options options)
        {
            this.name = name;
            this.input = input;
            this.output = output;
            this.options = options;
        }
    }

    @Test
    public void testNormalizeMain()
    {
        ArrayList<TestNormalize> tests = new ArrayList<>();
        NormalizeUrl.Options options;

        tests.add(new TestNormalize("main", "example.org", "http://example.org", null));
        tests.add(new TestNormalize("space", "example.org ", "http://example.org", null));
        tests.add(new TestNormalize("dot", "example.org.", "http://example.org", null));
        options = new NormalizeUrl.Options();
        options.defaultProtocol = "https";
        tests.add(new TestNormalize("default protocol",
                "example.org",
                "https://example.org",
                options));
        tests.add(new TestNormalize("upper case protocol",
                "HTTP://example.org",
                "http://example.org",
                null));
        tests.add(new TestNormalize("relative protocol",
                "//example.org",
                "http://example.org",
                null));
        options = new NormalizeUrl.Options();
        options.normalizeProtocol = false;
        tests.add(new TestNormalize("relative protocol (normalizeProtocol is false)",
                "//example.org",
                "//example.org",
                options));
        tests.add(new TestNormalize("relative protocol and default port (normalizeProtocol is false)",
                "//example.org:80",
                "//example.org",
                options));
        tests.add(new TestNormalize("http protocol",
                "http://example.org",
                "http://example.org",
                null));
        tests.add(new TestNormalize("port 80",
                "http://example.org:80",
                "http://example.org",
                null));
        tests.add(new TestNormalize("port 443",
                "https://example.org:443",
                "https://example.org",
                null));
        tests.add(new TestNormalize("port 21",
                "ftp://example.org:21",
                "ftp://example.org",
                null));
        tests.add(new TestNormalize("non default port",
                "http://example.org:5000",
                "http://example.org:5000",
                null));
        tests.add(new TestNormalize("www with protocol",
                "http://www.example.org",
                "http://example.org",
                null));
        tests.add(new TestNormalize("www as host", "www.com", "http://www.com", null));
        tests.add(new TestNormalize("double www",
                "http://www.www.example.org",
                "http://www.www.example.org",
                null));
        tests.add(new TestNormalize("www without protocol",
                "www.example.org",
                "http://example.org",
                null));
        tests.add(new TestNormalize("host with path",
                "http://example.org/foo",
                "http://example.org/foo",
                null));
        tests.add(new TestNormalize("whitespaces in query",
                "example.org/?foo=bar baz",
                "http://example.org/?foo=bar baz",
                null));
        tests.add(new TestNormalize("protocol in path",
                "https://foo.com/https://bar.com",
                "https://foo.com/https:/bar.com",
                null));
        tests.add(new TestNormalize("decode uri octets 1",
                "http://example.org/%7Efoo/",
                "http://example.org/~foo",
                null));
        tests.add(new TestNormalize("decode uri octets 2",
                "http://example.org/?foo=bar*%7C%3C%3E%3A%22",
                "http://example.org/?foo=bar*|<>:\"",
                null));
        options = new NormalizeUrl.Options();
        options.decode = false;
        tests.add(new TestNormalize("do not encode uri octets",
                "http://example.org/?foo=bar*%7C%3C%3E%3A%22",
                "http://example.org/?foo=bar*%7C%3C%3E%3A%22",
                options));
        tests.add(new TestNormalize("remove empty query",
                "http://example.org/?",
                "http://example.org",
                null));
        tests.add(new TestNormalize("unicode in host",
                "xn--xample-hva.com",
                "http://êxample.com",
                null));
        tests.add(new TestNormalize("sort query",
                "http://example.org/?b=bar&a=foo",
                "http://example.org/?a=foo&b=bar",
                null));
        tests.add(new TestNormalize("hash",
                "http://example.org/foo#bar",
                "http://example.org/foo#bar",
                null));
        tests.add(new TestNormalize("hash and trailing slash",
                "http://example.org/#/",
                "http://example.org/#/",
                null));
        options = new NormalizeUrl.Options();
        options.removeHash = true;
        tests.add(new TestNormalize("remove hash",
                "http://example.org/foo#bar",
                "http://example.org/foo",
                options));
        tests.add(new TestNormalize("relative path 1",
                "http://example.org/foo/bar/../baz",
                "http://example.org/foo/baz",
                null));
        tests.add(new TestNormalize("relative path 2",
                "http://example.org/foo/bar/./baz",
                "http://example.org/foo/bar/baz",
                null));
        tests.add(new TestNormalize("non standard protocol",
                "test://example.org",
                "test://example.org",
                null));
        tests.add(new TestNormalize("long url",
                "https://i.vimeocdn.com/filter/overlay?src0=https://i.vimeocdn.com/video/598160082_1280x720.jpg&src1=https://f.vimeocdn.com/images_v6/share/play_icon_overlay.png",
                "https://i.vimeocdn.com/filter/overlay?src0=https://i.vimeocdn.com/video/598160082_1280x720.jpg&src1=https://f.vimeocdn.com/images_v6/share/play_icon_overlay.png",
                null));
        options = new NormalizeUrl.Options();
        options.removeQueryParameters = new String[]{"utm_\\w+", "ref"};
        tests.add(new TestNormalize("remove query",
                "http://example.org?foo=bar&utm_medium=test&ref=test_ref",
                "http://example.org/?foo=bar",
                options));
        tests.add(new TestNormalize("query string in query value",
                "http://example.org?foo1=http://example.org?foo2=bar2",
                "http://example.org/?foo1=http://example.org?foo2=bar2",
                null));
        tests.add(new TestNormalize("query string in query value with '&'",
                "http://example.org?foo1=http://example.org?foo2=bar2&foo=bar",
                "http://example.org/?foo=bar&foo1=http://example.org?foo2=bar2",
                null));
        tests.add(new TestNormalize("port 8000",
                "http://example.org:8000",
                "http://example.org:8000",
                null));
        tests.add(new TestNormalize("port 8080",
                "http://example.org:8080",
                "http://example.org:8080",
                null));

        execTests(tests);
    }

    @Test
    public void testNormalize_removeAuth()
    {
        ArrayList<TestNormalize> tests = new ArrayList<>();

        tests.add(new TestNormalize("remove auth",
                "http://user:password@www.example.org",
                "http://example.org",
                null));
        tests.add(new TestNormalize("remove auth with fake username",
                "https://user:password@www.example.org/@user",
                "https://example.org/@user",
                null));
        NormalizeUrl.Options options = new NormalizeUrl.Options();
        options.removeAuthentication = false;
        tests.add(new TestNormalize("do not remove auth",
                "http://user:password@www.example.org",
                "http://user:password@example.org",
                options));
        tests.add(new TestNormalize("do not remove auth with fake username",
                "https://user:password@www.example.org/@user",
                "https://user:password@example.org/@user",
                options));

        execTests(tests);
    }

    @Test
    public void testNormalize_removeProtocol()
    {
        ArrayList<TestNormalize> tests = new ArrayList<>();

        NormalizeUrl.Options options = new NormalizeUrl.Options();
        options.removeProtocol = true;
        tests.add(new TestNormalize("http",
                "http://example.org",
                "example.org",
                options));
        tests.add(new TestNormalize("http with www",
                "http://www.example.org",
                "example.org",
                options));
        tests.add(new TestNormalize("https",
                "https://example.org",
                "example.org",
                options));
        tests.add(new TestNormalize("relative protocol",
                "//www.example.org",
                "example.org",
                options));
        tests.add(new TestNormalize("ftp",
                "ftp://example.org",
                "ftp://example.org",
                options));
        tests.add(new TestNormalize("custom protocol",
                "test://example.org",
                "test://example.org",
                options));

        execTests(tests);
    }

    @Test
    public void testNormalize_removeWWW()
    {
        ArrayList<TestNormalize> tests = new ArrayList<>();

        NormalizeUrl.Options options = new NormalizeUrl.Options();
        options.removeWWW = false;
        tests.add(new TestNormalize("http",
                "http://www.example.org",
                "http://www.example.org",
                options));
        tests.add(new TestNormalize("https",
                "https://www.example.org",
                "https://www.example.org",
                options));
        tests.add(new TestNormalize("without protocol",
                "www.example.org",
                "http://www.example.org",
                options));
        tests.add(new TestNormalize("relative protocol",
                "//www.example.org",
                "http://www.example.org",
                options));
        tests.add(new TestNormalize("custom protocol",
                "test://www.example.org",
                "test://www.example.org",
                options));

        execTests(tests);
    }

    @Test
    public void testNormalize_forceHttp()
    {
        ArrayList<TestNormalize> tests = new ArrayList<>();

        NormalizeUrl.Options options = new NormalizeUrl.Options();
        options.forceHttp = true;
        tests.add(new TestNormalize("http",
                "http://example.org",
                "http://example.org",
                options));
        tests.add(new TestNormalize("https",
                "https://example.org",
                "http://example.org",
                options));
        tests.add(new TestNormalize("without protocol",
                "example.org",
                "http://example.org",
                options));
        tests.add(new TestNormalize("relative protocol",
                "//example.org",
                "http://example.org",
                options));
        tests.add(new TestNormalize("custom protocol",
                "test://example.org",
                "test://example.org",
                options));

        execTests(tests);
    }

    @Test
    public void testNormalize_forceHttps()
    {
        ArrayList<TestNormalize> tests = new ArrayList<>();

        NormalizeUrl.Options options = new NormalizeUrl.Options();
        options.forceHttps = true;
        tests.add(new TestNormalize("http",
                "http://example.org",
                "https://example.org",
                options));
        tests.add(new TestNormalize("https",
                "https://example.org",
                "https://example.org",
                options));
        tests.add(new TestNormalize("without protocol",
                "example.org",
                "https://example.org",
                options));
        tests.add(new TestNormalize("relative protocol",
                "//example.org",
                "https://example.org",
                options));
        tests.add(new TestNormalize("custom protocol",
                "test://example.org",
                "test://example.org",
                options));

        execTests(tests);
    }

    @Test
    public void testNormalize_forceHttp_with_forceHttps()
    {
        NormalizeUrl.Options options = new NormalizeUrl.Options();
        options.forceHttp = true;
        options.forceHttps = true;

        try {
            NormalizeUrl.normalize("example.org", options);
            fail();
        } catch (Exception e) {
            /* Asserts */
        }
    }

    @Test
    public void testNormalize_removeDirectoryIndex()
    {
        ArrayList<TestNormalize> tests = new ArrayList<>();

        NormalizeUrl.Options options = new NormalizeUrl.Options();
        options.removeDirectoryIndex = new String[]{"index.html", "index.php"};
        tests.add(new TestNormalize("index.html 1",
                "http://example.org/index.html",
                "http://example.org",
                options));
        tests.add(new TestNormalize("index.htm 1",
                "http://example.org/index.htm",
                "http://example.org/index.htm",
                options));
        tests.add(new TestNormalize("index.php 1",
                "http://example.org/index.php",
                "http://example.org",
                options));
        tests.add(new TestNormalize("foo/bar/index.html 1",
                "http://example.org/foo/bar/index.html",
                "http://example.org/foo/bar",
                options));
        tests.add(new TestNormalize("empty path",
                "http://example.org",
                "http://example.org",
                options));

        options = new NormalizeUrl.Options();
        options.removeDirectoryIndex = new String[]{"^index\\.[a-z]+$"};
        tests.add(new TestNormalize("index.html 2",
                "http://example.org/index.html",
                "http://example.org",
                options));
        tests.add(new TestNormalize("index.htm 2",
                "http://example.org/index.htm",
                "http://example.org",
                options));
        tests.add(new TestNormalize("index.php 2",
                "http://example.org/index.php",
                "http://example.org",
                options));
        tests.add(new TestNormalize("foo/bar/index.html 2",
                "http://example.org/foo/bar/index.html",
                "http://example.org/foo/bar",
                options));
        tests.add(new TestNormalize("default.html 2",
                "http://example.org/default.html",
                "http://example.org/default.html",
                options));

        execTests(tests);
    }

    @Test
    public void testNormalize_removeDirectoryIndex_with_removeTrailingSlash()
    {
        ArrayList<TestNormalize> tests = new ArrayList<>();

        NormalizeUrl.Options options = new NormalizeUrl.Options();
        options.removeDirectoryIndex = new String[]{"^index\\.[a-z]+$"};
        options.removeTrailingSlash = true;
        tests.add(new TestNormalize("foo/ 1",
                "http://example.org/foo/",
                "http://example.org/foo",
                options));
        tests.add(new TestNormalize("foo/index.html 1",
                "http://example.org/foo/index.html",
                "http://example.org/foo",
                options));
        tests.add(new TestNormalize("#/foo/ 1",
                "http://example.org/#/foo/",
                "http://example.org/#/foo/",
                options));
        tests.add(new TestNormalize("foo/#/bar/ 1",
                "http://example.org/foo/#/bar/",
                "http://example.org/foo#/bar/",
                options));

        options = new NormalizeUrl.Options();
        options.removeDirectoryIndex = new String[]{"^index\\.[a-z]+$"};
        options.removeTrailingSlash = false;
        tests.add(new TestNormalize("foo/ 2",
                "http://example.org/foo/",
                "http://example.org/foo/",
                options));
        tests.add(new TestNormalize("foo/index.html 2",
                "http://example.org/foo/index.html",
                "http://example.org/foo/",
                options));
        tests.add(new TestNormalize("#/foo/ 2",
                "http://example.org/#/foo/",
                "http://example.org/#/foo/",
                options));
        tests.add(new TestNormalize("foo/#/bar/ 2",
                "http://example.org/foo/#/bar/",
                "http://example.org/foo/#/bar/",
                options));

        execTests(tests);
    }

    @Test
    public void testNormalize_removeDuplicateSlashes()
    {
        ArrayList<TestNormalize> tests = new ArrayList<>();

        tests.add(new TestNormalize("//foo",
                "http://example.org//foo",
                "http://example.org/foo",
                null));
        tests.add(new TestNormalize("///foo",
                "http://example.org///foo",
                "http://example.org/foo",
                null));
        tests.add(new TestNormalize("////foo/bar",
                "http://example.org////foo/bar",
                "http://example.org/foo/bar",
                null));
        tests.add(new TestNormalize("////foo////bar",
                "http://example.org////foo////bar",
                "http://example.org/foo/bar",
                null));
        NormalizeUrl.Options options = new NormalizeUrl.Options();
        options.normalizeProtocol = false;
        tests.add(new TestNormalize("double slashes and relative protocol",
                "//example.org//foo",
                "//example.org/foo",
                options));
        tests.add(new TestNormalize("double slashes and port",
                "http://example.org:5000///foo",
                "http://example.org:5000/foo",
                null));
        tests.add(new TestNormalize("double slashes and port 8000",
                "http://example.org:8000///foo",
                "http://example.org:8000/foo",
                null));
        tests.add(new TestNormalize("double slashes and port 8080",
                "http://example.org:8080///foo",
                "http://example.org:8080/foo",
                null));

        execTests(tests);
    }

    private void execTests(ArrayList<TestNormalize> tests)
    {
        for (TestNormalize test : tests) {
            try {
                String output = NormalizeUrl.normalize(test.input, test.options);
                assertEquals(test.name, test.output, output);

            } catch (Exception e) {
                fail(String.format("%s: %s cause: %s", test.name, e.getMessage(), e.getCause()));
            }
        }
    }
}