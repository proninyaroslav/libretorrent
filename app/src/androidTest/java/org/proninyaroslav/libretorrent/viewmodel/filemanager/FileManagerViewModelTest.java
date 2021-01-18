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

package org.proninyaroslav.libretorrent.viewmodel.filemanager;

import android.net.Uri;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.proninyaroslav.libretorrent.AbstractTest;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerViewModel;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FileManagerViewModelTest extends AbstractTest
{
    private FileManagerViewModel viewModel;
    private FileManagerConfig config;

    @Before
    public void init()
    {
        super.init();

        config = new FileManagerConfig(fs.getUserDirPath(),
                null, FileManagerConfig.DIR_CHOOSER_MODE);
        viewModel = new FileManagerViewModel(
                ApplicationProvider.getApplicationContext(),
                config,
                fs.getUserDirPath()
        );
    }

    @Test
    public void testOpenDirectory()
    {
        try {
            viewModel.jumpToDirectory(fs.getUserDirPath());
            viewModel.openDirectory("Android");
            Uri path = viewModel.getCurDirectoryUri();
            assertNotNull(path);
            assertTrue(path.getPath().endsWith("Android"));

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        }
    }

    @Test
    public void testJumpToDirectory()
    {
        try {
            viewModel.jumpToDirectory(fs.getUserDirPath() + "/Android");
            Uri path = viewModel.getCurDirectoryUri();
            assertNotNull(path);
            assertTrue(path.getPath().endsWith("Android"));

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        }
    }

    @Test
    public void testUpToParentDirectory()
    {
        try {
            viewModel.jumpToDirectory(fs.getUserDirPath() + "/Android");
            viewModel.upToParentDirectory();
            Uri path = viewModel.getCurDirectoryUri();
            assertNotNull(path);
            assertFalse(path.getPath().endsWith("Android"));

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        }
    }

    @Test
    public void testCreateFile()
    {
        File f = null;
        try {
            viewModel.jumpToDirectory(fs.getUserDirPath());
            viewModel.createFile("test.txt");
            Uri filePath = viewModel.getFileUri("test.txt");
            assertNotNull(filePath);
            f = new File(filePath.getPath());
            assertTrue(f.exists());

        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        } finally {
            if (f != null)
                f.delete();
        }
    }

    @Test
    public void testPermissionDenied()
    {
        try {
            viewModel.jumpToDirectory(fs.getUserDirPath());
            viewModel.upToParentDirectory();

        } catch (SecurityException e) {
            return;
        } catch (Exception e) {
            fail(Log.getStackTraceString(e));
        }

        fail("Permission available");
    }
}