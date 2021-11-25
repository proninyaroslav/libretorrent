/*
 * Copyright (C) 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * This file is part of LibreTorrent.
 *
 * LibreTorrent is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LibreTorrent is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LibreTorrent.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.proninyaroslav.libretorrent.core.system;

import android.net.Uri;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.proninyaroslav.libretorrent.AbstractTest;
import org.proninyaroslav.libretorrent.core.exception.UnknownUriException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class FileSystemFacadeTest extends AbstractTest
{
    private FileSystemFacadeImpl fakeFs;
    private FakeFsModuleResolver fsResolver;
    private Uri dirUri = Uri.parse("file:///root");

    private static final long FILE_10_MB_SIZE = 1024 * 1024 * 10;
    private static final long FILE_5_MB_SIZE = 1024 * 1024 * 5;
    private Uri dir;

    @Override
    public void init()
    {
        super.init();

        dir = Uri.parse("file://" + fs.getDefaultDownloadPath());
        fsResolver = new FakeFsModuleResolver();
        fakeFs = new FileSystemFacadeImpl(context, fsResolver);
    }

    @Test
    public void deleteFile() throws UnknownUriException
    {
        fsResolver.existsFileNames = Collections.singletonList("test.txt");
        try {
            assertTrue(fakeFs.deleteFile(Uri.parse("file:///root/test.txt")));

        } catch (FileNotFoundException e) {
            fail(Log.getStackTraceString(e));

        } finally {
            fsResolver.existsFileNames = null;
        }
    }

    @Test
    public void getFileUri() throws UnknownUriException
    {
        fsResolver.existsFileNames = Collections.singletonList("test.txt");
        assertEquals("file:///root/test.txt", fakeFs.getFileUri(dirUri, "test.txt").toString());
        fsResolver.existsFileNames = Collections.singletonList("bar");
        assertNull(fakeFs.getFileUri(dirUri, "test.txt"));
        fsResolver.existsFileNames = null;
    }

    @Test
    public void getFileUri_relativePath() throws UnknownUriException
    {
        fsResolver.existsFileNames = Collections.singletonList("bar.txt");
        assertEquals("file:///root/foo/bar.txt", fakeFs.getFileUri("foo/bar.txt", dirUri).toString());
        fsResolver.existsFileNames = Collections.singletonList("test.txt");
        assertNull(fakeFs.getFileUri("foo/bar.txt", dirUri));
        fsResolver.existsFileNames = null;
    }

    @Test
    public void createFile() throws UnknownUriException
    {
        try {
            fsResolver.existsFileNames = Collections.singletonList("test.txt");
            assertEquals("file:///root/test.txt", fakeFs.createFile(dirUri, "test.txt", false).toString());
            assertEquals("file:///root/test.txt", fakeFs.createFile(dirUri, "test.txt", true).toString());
            fsResolver.existsFileNames = Collections.singletonList("foo");
            assertEquals("file:///root/test.txt", fakeFs.createFile(dirUri, "test.txt", false).toString());
            assertEquals("file:///root/test.txt", fakeFs.createFile(dirUri, "test.txt", true).toString());

        } catch (IOException e) {
            fail(Log.getStackTraceString(e));

        } finally {
            fsResolver.existsFileNames = null;
        }
    }

    @Test
    public void getExtension()
    {
        assertEquals("txt", fakeFs.getExtension("test.txt"));
        assertEquals("png", fakeFs.getExtension("test.png"));
        assertEquals("", fakeFs.getExtension("test"));
        assertEquals("png", fakeFs.getExtension("test.foo.png"));
    }

    @Test
    public void isValidFatFilename()
    {
        assertTrue(fakeFs.isValidFatFilename("Valid_012345"));
        assertFalse(fakeFs.isValidFatFilename("IVALID*012345"));
        assertFalse(fakeFs.isValidFatFilename(""));
        assertFalse(fakeFs.isValidFatFilename(String.join(" ", Collections.nCopies(256, "long string"))));
    }

    @Test
    public void buildValidFatFilename()
    {
        assertEquals("Valid_012345", fakeFs.buildValidFatFilename("Valid_012345"));
        assertEquals("(invalid)", fakeFs.buildValidFatFilename(""));
        assertEquals("IVALID_012345", fakeFs.buildValidFatFilename("IVALID*012345"));
    }

    @Test
    public void getDirName() throws UnknownUriException
    {
        fsResolver.existsFileNames = Collections.singletonList("bar");
        assertEquals("bar", fakeFs.getDirPath(Uri.parse("file///root/bar")));
        fsResolver.existsFileNames = null;
    }

    @Test
    public void fileExists() throws UnknownUriException
    {
        fsResolver.existsFileNames = Collections.singletonList("test.txt");
        assertTrue(fakeFs.fileExists(Uri.parse("file///root/test.txt")));
        fsResolver.existsFileNames = Collections.singletonList("bar");
        assertFalse(fakeFs.fileExists(Uri.parse("file///root/test.txt")));
        fsResolver.existsFileNames = null;
    }

    @Test
    public void makeFileSystemPath() throws UnknownUriException
    {
        fsResolver.existsFileNames = Collections.singletonList("bar.txt");
        assertEquals("/root/foo/bar.txt", fakeFs.makeFileSystemPath(dirUri, "foo/bar.txt"));
        fsResolver.existsFileNames = Collections.singletonList("test.txt");
        assertNull(fakeFs.makeFileSystemPath(dirUri, "foo/bar.txt"));
        fsResolver.existsFileNames = null;
    }
}