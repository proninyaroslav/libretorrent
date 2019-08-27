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

package org.proninyaroslav.libretorrent.core;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.proninyaroslav.libretorrent.AbstractTest;
import org.proninyaroslav.libretorrent.core.filesystem.SafFileSystem;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerDialog;

import java.io.FileNotFoundException;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SafFileSystemTest extends AbstractTest
{
    private Uri safRoot;
    private SafFileSystem fs;
    private final String file1 = "foo.txt";
    private final String dir2 = "foo";
    private final String file2 = dir2 + "/bar.txt";

    @Override
    public void init()
    {
        super.init();

        fs = SafFileSystem.getInstance(context);

        Intent i = new Intent(context, FileManagerDialog.class);

        FileManagerConfig config = new FileManagerConfig(null,
                null,
                FileManagerConfig.DIR_CHOOSER_MODE);

        i.putExtra(FileManagerDialog.TAG_CONFIG, config);
        ActivityScenario<FileManagerDialog> scenario = ActivityScenario.launch(i);
        safRoot = scenario.getResult().getResultData().getData();
        assertNotNull(safRoot);
        assertTrue("required SAF path", fs.isSafPath(safRoot));
    }

    @Test
    public void testFakePath()
    {
        String safPath = safRoot.toString().substring("content://".length());
        String fakePathStr = "saf_root(" + safPath+ ");" + file2;

        SafFileSystem.FakePath fakePath = new SafFileSystem.FakePath(safRoot, file2);
        assertEquals("serialize", fakePathStr, fakePath.toString());

        SafFileSystem.FakePath deserialized = SafFileSystem.FakePath.deserialize(fakePathStr);
        assertNotNull(deserialized);
        assertEquals("deserialize saf root", safRoot, deserialized.safRoot());
        assertEquals("deserialize relative path", file2, deserialized.relativePath());
    }

    @Test
    public void testFileSystemApi()
    {
        Uri f1 = null;
        Uri f2 = null;
        SafFileSystem.FakePath fakePathDir = new SafFileSystem.FakePath(safRoot, dir2);
        SafFileSystem.FakePath fakePath = new SafFileSystem.FakePath(safRoot, file2);
        SafFileSystem.FakePath fakePathNestedDir = new SafFileSystem.FakePath(safRoot, dir2 + "/foo/bar");

        try {
            f1 = fs.getFileUri(safRoot, file1, false);
            assertNull("file1 doesn't exists", f1);
            f1 = fs.getFileUri(safRoot, file1, true);
            assertNotNull("file1 doesn't exists and create it", f1);
            f2 = fs.getFileUri(fakePath, false);
            assertNull("file2 doesn't exists", f2);
            f2 = fs.getFileUri(fakePath, true);
            assertNotNull("file2 doesn't exists and create it", f2);

            assertTrue("file1 exists", fs.exists(f1));
            assertTrue("file2 exists", fs.exists(f2));
            assertTrue("dir2 exists", fs.exists(fakePathDir));

            SafFileSystem.Stat stat1 = fs.stat(f1);
            assertNotNull(stat1);
            assertEquals("[stat] file1 name equals", file1, stat1.name);
            assertFalse("[stat] file1 is not dir", stat1.isDir);
            SafFileSystem.Stat stat2 = fs.stat(fakePathDir);
            assertNotNull(stat2);
            assertEquals("[stat] dir2 name equals", dir2, stat2.name);
            assertTrue("[stat] dir2 is dir", stat2.isDir);

            assertTrue("mkdir", fs.mkdirs(fakePathNestedDir));
            Uri f3 = fs.getFileUri(fakePathNestedDir, false);
            assertNotNull("[mkdirs] get created file", f3);
            SafFileSystem.Stat stat3 = fs.stat(f3);
            assertNotNull("[mkdirs] get stat", stat3);
            assertEquals("[mkdirs] compare name", "bar", stat3.name);
            assertTrue("[mkdirs] is dir", stat3.isDir);

            assertNotEquals("open fd for file1", -1, fs.openFD(f1, "rw"));
            assertNotEquals("open fd for fake path file2", -1, fs.openFD(fakePath, "rw"));

        } finally {
            try {
                if (f1 != null)
                    assertTrue("delete file1", fs.delete(f1));
                assertTrue("delete empty dir2", fs.delete(fakePathDir));

            } catch (FileNotFoundException e) {
                fail("delete file error: " + Log.getStackTraceString(e));
            }
        }
    }

    @Test
    public void testCaching()
    {
        Uri f1 = null;
        try {
            f1 = fs.getFileUri(safRoot, file1, true);
            assertNotNull(f1);

            SafFileSystem.Stat stat1 = fs.stat(f1);
            SafFileSystem.Stat stat2 = fs.stat(f1);

            assertNotNull(stat1);
            assertNotNull(stat2);
            assertEquals("name", stat1.name, stat2.name);
            assertEquals("isDir", stat1.isDir, stat2.isDir);
            assertEquals("length", stat1.length, stat2.length);
            assertEquals("lastModified", stat1.lastModified, stat1.lastModified);

        } finally {
            try {
                if (f1 != null)
                    fs.delete(f1);

            } catch (FileNotFoundException e) {
                /* Ignore */
            }
        }
    }
}