/*
 * Copyright (C) 2019, 2020 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.filemanager;

import android.app.Application;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.databinding.ObservableField;
import androidx.lifecycle.AndroidViewModel;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.exception.UnknownUriException;
import org.proninyaroslav.libretorrent.core.model.filetree.FileNode;
import org.proninyaroslav.libretorrent.core.system.FileSystemFacade;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.reactivex.subjects.BehaviorSubject;

public class FileManagerViewModel extends AndroidViewModel
{
    private static final String TAG = FileManagerViewModel.class.getSimpleName();

    private FileSystemFacade fs;
    public String startDir;
    /* Current directory */
    public ObservableField<String> curDir = new ObservableField<>();
    public FileManagerConfig config;
    public BehaviorSubject<List<FileManagerNode>> childNodes = BehaviorSubject.create();
    public Exception errorReport;

    public FileManagerViewModel(
            @NonNull Application application,
            FileManagerConfig config,
            String startDir
    ){
        super(application);

        this.config = config;
        this.fs = SystemFacadeHelper.getFileSystemFacade(application);
        this.startDir = startDir;

        String path = config.path;
        if (!TextUtils.isEmpty(path) && startDir == null) {
            startDir = path;
        }
        if (startDir == null) {
            startDir = fs.getDefaultDownloadPath();
        }
        File dir = new File(startDir);
        boolean canAccess = checkPermissions(dir, config);
        if (!(dir.exists() && canAccess)) {
            startDir = fs.getDefaultDownloadPath();
        }

        try {
            if (startDir != null) {
                startDir = new File(startDir).getCanonicalPath();
            }
            updateCurDir(startDir);

        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    public void refreshCurDirectory()
    {
        childNodes.onNext(getChildItems());
    }

    private void updateCurDir(String newPath)
    {
        if (newPath == null)
            return;
        curDir.set(newPath);
        childNodes.onNext(getChildItems());
    }

    /*
     * Get subfolders or files.
     */

    private List<FileManagerNode> getChildItems()
    {
        var items = new ArrayList<FileManagerNode>();
        var dir = curDir.get();
        if (dir == null)
            return items;

        try {
            var dirFile = new File(dir);
            if (!(dirFile.exists() && dirFile.isDirectory()))
                return items;

            /* Adding parent dir for navigation */
            if (!dirFile.getPath().equals(FileManagerNode.ROOT_DIR))
                items.add(0, new FileManagerNode(FileManagerNode.PARENT_DIR, FileNode.Type.DIR, true));

            var files = dirFile.listFiles();
            if (files == null)
                return items;
            for (var file : filterDirectories(files)) {
                if (file.isDirectory())
                    items.add(new FileManagerNode(file.getName(), FileNode.Type.DIR, true));
                else
                    items.add(new FileManagerNode(file.getName(), FileManagerNode.Type.FILE,
                            config.showMode == FileManagerConfig.FILE_CHOOSER_MODE));
            }

        } catch (Exception e) {
            /* Ignore */
        }

        return items;
    }

    List<File> filterDirectories(File[] files) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                || config.showMode == FileManagerConfig.FILE_CHOOSER_MODE) {
            return Arrays.asList(files);
        }

        var filtered = new ArrayList<File>();
        for (var file : files) {
            if (file.isFile() || file.canWrite()) {
                filtered.add(file);
            }
        }

        return filtered;
    }

    public boolean createDirectory(String name)
    {
        if (TextUtils.isEmpty(name))
            return false;

        File newDir = new File(curDir.get(), name);

        return !newDir.exists() && newDir.mkdir();
    }

    public void openDirectory(String name) throws IOException, SecurityException
    {
        File dir = new File(curDir.get(), name);
        String path = dir.getCanonicalPath();

        if (!(dir.exists() && dir.isDirectory()))
            path = startDir;
        else if (!checkPermissions(dir, config))
            throw new SecurityException("Permission denied");

        updateCurDir(path);
    }

    public void jumpToDirectory(String path) throws SecurityException
    {
        File dir = new File(path);

        if (!(dir.exists() && dir.isDirectory()))
            path = startDir;
        else if (!checkPermissions(dir, config))
            throw new SecurityException("Permission denied");

        updateCurDir(path);
    }

    /*
     * Navigate back to an upper directory.
     */

    public void upToParentDirectory() throws SecurityException
    {
        String path = curDir.get();
        if (path == null)
            return;
        File dir = new File(path);
        File parentDir = dir.getParentFile();
        if (parentDir != null && !checkPermissions(parentDir, config))
            throw new SecurityException("Permission denied");

        updateCurDir(dir.getParent());
    }

    public boolean fileExists(String fileName)
    {
        if (fileName == null)
            return false;

        fileName = appendExtension(fileName);

        return new File(curDir.get(), fileName).exists();
    }

    private String appendExtension(String fileName)
    {
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        String extension = fs.getExtension(fileName);

        if (TextUtils.isEmpty(extension)) {
            extension = mimeTypeMap.getExtensionFromMimeType(config.mimeType);
        } else {
            String mimeType = mimeTypeMap.getMimeTypeFromExtension(extension);
            if (mimeType == null || !mimeType.equals(config.mimeType))
                extension = mimeTypeMap.getExtensionFromMimeType(config.mimeType);
        }

        if (extension != null && !fileName.endsWith(extension))
            fileName += fs.getExtensionSeparator() + extension;

        return fileName;
    }

    public Uri createFile(String fileName) throws SecurityException
    {
        if (TextUtils.isEmpty(fileName))
            fileName = config.fileName;

        fileName = appendExtension(fs.buildValidFatFilename(fileName));

        File f = new File(curDir.get(), fileName);
        File parent = f.getParentFile();
        if (parent != null && !checkPermissions(parent, config))
            throw new SecurityException("Permission denied");
        try {
            if (f.exists() && !f.delete())
                return null;
            if (!f.createNewFile())
                return null;

        } catch (IOException e) {
            return null;
        }

        return Uri.fromFile(f);
    }

    public Uri getCurDirectoryUri() throws SecurityException
    {
        String path = curDir.get();
        if (path == null)
            return null;

        File dir = new File(path);
        if (!checkPermissions(dir, config))
            throw new SecurityException("Permission denied");

        return Uri.fromFile(dir);
    }

    public Uri getFileUri(String fileName) throws SecurityException
    {
        String path = curDir.get();
        if (path == null)
            return null;

        File f = new File(path, fileName);
        if (!checkPermissions(f, config))
            throw new SecurityException("Permission denied");

        return Uri.fromFile(f);
    }

    private List<Uri> getExtSdCardPaths() {
        List<Uri> uriList = new ArrayList<>();
        File[] externals = ContextCompat.getExternalFilesDirs(getApplication(), "external");
        File external = getApplication().getExternalFilesDir("external");
        for (File file : externals) {
            if (file != null && !file.equals(external)) {
                String absolutePath = file.getAbsolutePath();
                String path = getBaseSdCardPath(absolutePath);
                if (path == null || !checkPermissions(new File(path), config)) {
                    path = getSdCardDataPath(absolutePath);
                    if (path == null || !checkPermissions(new File(path), config)) {
                        Log.w(TAG, "Ext sd card path wrong: " + absolutePath);
                        continue;
                    }
                }
                uriList.add(Uri.parse("file://" + path));
            }
        }

        return uriList;
    }

    private String getBaseSdCardPath(String absolutePath) {
        int index = absolutePath.lastIndexOf("/Android/data");
        if (index >= 0) {
            return tryGetCanonicalPath(absolutePath.substring(0, index));
        } else {
            return null;
        }
    }

    private String getSdCardDataPath(String absolutePath) {
        int index = absolutePath.lastIndexOf("/external");
        if (index >= 0) {
            return tryGetCanonicalPath(absolutePath.substring(0, index));
        } else {
            return null;
        }
    }

    private String tryGetCanonicalPath(String absolutePath) {
        try {
            return new File(absolutePath).getCanonicalPath();
        } catch (IOException e) {
            // Keep non-canonical path.
            return absolutePath;
        }
    }

    private boolean checkPermissions(File file, FileManagerConfig config) {
        switch (config.showMode) {
            case FileManagerConfig.FILE_CHOOSER_MODE:
                return file.canRead();
            case FileManagerConfig.DIR_CHOOSER_MODE:
                return file.canRead() && file.canWrite();
            case FileManagerConfig.SAVE_FILE_MODE:
                return file.canWrite();
        }

        throw new IllegalArgumentException("Unknown mode: " + config.showMode);
    }

    public List<FileManagerSpinnerAdapter.StorageSpinnerItem> getStorageList()
    {
        ArrayList<FileManagerSpinnerAdapter.StorageSpinnerItem> items = new ArrayList<>();
        List<Uri> storageList = getExtSdCardPaths();

        Uri primaryStorage = Uri.fromFile(Environment.getExternalStorageDirectory());
        try {
            items.add(new FileManagerSpinnerAdapter.StorageSpinnerItem(
                    getApplication().getString(R.string.internal_storage_name),
                    fs.getDirPath(primaryStorage),
                    fs.getDirAvailableBytes(primaryStorage))
            );
        } catch (UnknownUriException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }

        if (!storageList.isEmpty()) {
            for (int i = 0; i < storageList.size(); i++) {
                String template = getApplication().getString(R.string.external_storage_name);
                try {
                    items.add(new FileManagerSpinnerAdapter.StorageSpinnerItem(
                            String.format(template, i + 1),
                            storageList.get(i).getPath(),
                            fs.getDirAvailableBytes(storageList.get(i)))
                    );
                } catch (UnknownUriException e) {
                    Log.e(TAG, Log.getStackTraceString(e));
                }
            }
        }

        return items;
    }
}