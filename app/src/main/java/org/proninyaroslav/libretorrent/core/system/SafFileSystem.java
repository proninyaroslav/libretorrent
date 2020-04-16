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

package org.proninyaroslav.libretorrent.core.system;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileNotFoundException;

/*
 * A class that representing a wrapper around SAF (Storage Access Framework) for
 * providing API for working with files that SAF provides by their Uri.
 * "SAF root" in this context means a tree Uri obtained by calling
 * `Intent.ACTION_OPEN_DOCUMENT_TREE`, so many methods don't work with Android 4.4, only with API >= 21.
 *
 * `SafFileSystem` also supports the so-called fake paths to files (`FakePath` class),
 * which is a hybrid of the SAF path and the classic system path, making such path valid for passing
 * through an object of `File` class (Note: `File` class still can't work with this path,
 * for example, creating or deleting a file). This is makes for compatibility with `libtorrent`
 * (via `LibTorrentSafAdapter`), which works with the classic `File` object.
 *
 * Fake path example: '/saf_root(com.android.externalstorage.documents/tree/volumeId/);/foo/bar.txt'
 *
 *     'saf_root()'  - a special path format that encapsulates the SAF path and helps
 *                     distinguish it from the rest of the path.
 *                     You can consider this as a special name for the classic root ('/') dir,
 *                     but in the SAF context (Uri obtained by calling `Intent.ACTION_OPEN_DOCUMENT_TREE`)
 *     'foo/bar.txt' - a relative path (optional), regarding SAF root, which is represented in the
 *                     normal system format.
 */

public class SafFileSystem
{
    @SuppressWarnings("unused")
    private static final String TAG = SafFileSystem.class.getSimpleName();

    private static final int CACHE_MAX_SIZE = 1000;

    private static volatile SafFileSystem INSTANCE;
    private Context appContext;
    private static final LruCache<String, DocumentFile> CACHE = new LruCache<>(CACHE_MAX_SIZE);

    public static SafFileSystem getInstance(@NonNull Context appContext)
    {
        if (INSTANCE == null) {
            synchronized (SafFileSystem.class) {
                if (INSTANCE == null)
                    INSTANCE = new SafFileSystem(appContext);
            }
        }

        return INSTANCE;
    }

    private SafFileSystem(Context appContext)
    {
        this.appContext = appContext;
    }

    public static class FakePath
    {
        private static final String SAF_ROOT_TAG_OPEN = "saf_root(";
        private static final String SAF_ROOT_TAG_CLOSE = ");";
        private static final String SCHEME = "content://";

        private Uri safRoot;
        private String relativePath;

        public FakePath(@NonNull Uri safRoot, @NonNull String relativePath)
        {
            this.safRoot = safRoot;
            this.relativePath = normalizeRelativePath(relativePath);
        }

        private String normalizeRelativePath(String path)
        {
            if (path.equals(File.separator) || !path.startsWith(File.separator))
                return path;
            else
                return path.substring(1);
        }

        /*
         * Returns string path in the following format:
         *     'saf_root(`safRoot`);`relativePath (optional)`'
         * or null if the path is invalid
         */

        public static FakePath deserialize(@NonNull String fakePath)
        {
            int openTagIdx = fakePath.lastIndexOf(SAF_ROOT_TAG_OPEN);
            int closeTagIdx = fakePath.lastIndexOf(SAF_ROOT_TAG_CLOSE);
            if (openTagIdx < 0 || closeTagIdx < 0 || closeTagIdx <= openTagIdx)
                return null;

            String safRootStr = fakePath.substring(openTagIdx + SAF_ROOT_TAG_OPEN.length(), closeTagIdx);
            Uri safRoot = Uri.parse(SCHEME + safRootStr);
            String relativePath = fakePath.substring(closeTagIdx + SAF_ROOT_TAG_CLOSE.length());

            return new FakePath(safRoot, relativePath);
        }

        private String serialize()
        {
            /* Remove 'content://' scheme */
            String safRootStr = safRoot.toString().substring(SCHEME.length());

            return SAF_ROOT_TAG_OPEN + safRootStr + SAF_ROOT_TAG_CLOSE + relativePath;
        }

        public String[] makeRelativePathNodes()
        {
            return (TextUtils.isEmpty(relativePath) ?
                    new String[0] :
                    relativePath.split(File.separator));
        }

        public Uri safRoot()
        {
            return safRoot;
        }

        public String relativePath()
        {
            return relativePath;
        }

        public static boolean isFakePath(@NonNull String path)
        {
            return path.startsWith("/" + SAF_ROOT_TAG_OPEN) || path.startsWith(SAF_ROOT_TAG_OPEN);
        }

        @NonNull
        @Override
        public String toString()
        {
            return serialize();
        }
    }

    public static class Stat
    {
        public String name;
        public boolean isDir;
        public long length;
        public long lastModified;

        public Stat(String name, boolean isDir,
                    long length, long lastModified)
        {
            this.name = name;
            this.isDir = isDir;
            this.length = length;
            this.lastModified = lastModified;
        }
    }

    /*
     * Return true if the uri is a SAF path
     */

    public boolean isSafPath(@NonNull Uri path)
    {
        String scheme = path.getScheme();
        if (scheme == null)
            throw new IllegalArgumentException("Scheme of " + path + " is null");

        return scheme.equals(ContentResolver.SCHEME_CONTENT);
    }

    /*
     * Returns Uri of the file by the given file name or
     * null if the file doesn't exists
     */

    @TargetApi(21)
    @Nullable
    public Uri getFileUri(@NonNull Uri safRoot, @NonNull String fileName, boolean create)
    {
        String cacheKey = safRoot.toString() + File.separator + fileName;
        DocumentFile f = CACHE.get(cacheKey);

        if (f == null) {
            DocumentFile tree = DocumentFile.fromTreeUri(appContext, safRoot);
            if (tree == null)
                return null;
            f = getFile(tree, fileName, create);
            if (f != null)
                CACHE.put(cacheKey, f);
        }

        return (f == null ? null : f.getUri());
    }

    /*
     * Returns Uri of the last file in the given path and
     * creates it (and also dirs), if `create` parameter is true.
     * Еhe last file is always created as a file, even if it was meant as a directory.
     * For example, for path='/saf_root/foo/bar.txt' method returns
     * Uri of the 'bar.txt' file or null if the file
     * doesn't exists or one of the directories in the path doesn't exist.
     */

    @TargetApi(21)
    @Nullable
    public Uri getFileUri(@NonNull FakePath path, boolean create)
    {
        String cacheKey = path.toString();
        DocumentFile f = CACHE.get(cacheKey);
        if (f == null) {
            f = getFile(path, create);
            if (f != null)
                CACHE.put(cacheKey, f);
        }

        return (f == null ? null : f.getUri());
    }

    private DocumentFile getFile(DocumentFile tree, String fileName, boolean create)
    {
        try {
            DocumentFile f = tree.findFile(fileName);
            if (f == null && create)
                f = tree.createFile("application/octet-stream", fileName);

            return (f != null && f.isFile() ? f : null);

        } catch (UnsupportedOperationException e) {
            return null;
        }
    }

    private DocumentFile getFile(FakePath path, boolean create)
    {
        DocumentFile currNode = DocumentFile.fromTreeUri(appContext, path.safRoot());
        if (currNode == null)
            return null;

        String[] nodes = path.makeRelativePathNodes();
        for (int i = 0; i < nodes.length; i++) {
            String nodeName = nodes[i];

            DocumentFile node;
            if (i + 1 == nodes.length) {
                node = getFile(currNode, nodeName, create);
                /* Try to get directory */
                if (node == null)
                    node = getDir(currNode, nodeName, create);
            } else {
                node = getDir(currNode, nodeName, create);
            }

            if (node == null)
                return null;

            currNode = node;
        }

        return currNode;
    }

    private DocumentFile getDir(DocumentFile tree, String dirName, boolean create)
    {
        try {
            DocumentFile f = tree.findFile(dirName);
            if (f == null && create)
                f = tree.createDirectory(dirName);

            return (f != null && f.isDirectory() ? f : null);

        } catch (UnsupportedOperationException e) {
            return null;
        }
    }

    /*
     * Returns true if the file was deleted successfully
     */

    @TargetApi(19)
    public boolean delete(@NonNull Uri filePath) throws FileNotFoundException
    {
        return DocumentsContract.deleteDocument(appContext.getContentResolver(), filePath);
    }

    @TargetApi(19)
    public boolean delete(@NonNull FakePath path) throws FileNotFoundException
    {
        Uri filePath = getFileUri(path, false);
        if (filePath == null)
            return false;

        return DocumentsContract.deleteDocument(appContext.getContentResolver(), filePath);
    }

    /*
     * Returns true if the file is exists
     */

    @TargetApi(19)
    public boolean exists(@NonNull Uri filePath)
    {
        String cacheKey = filePath.toString();
        DocumentFile f = CACHE.get(cacheKey);
        if (f == null) {
            f = DocumentFile.fromSingleUri(appContext, filePath);
            if (f != null)
                CACHE.put(cacheKey, f);
        }

        return f != null && f.exists();
    }

    @TargetApi(21)
    public boolean exists(@NonNull FakePath path)
    {
        String cacheKey = path.toString();
        DocumentFile cached = CACHE.get(cacheKey);
        if (cached != null)
            return cached.exists();

        DocumentFile currNode = DocumentFile.fromTreeUri(appContext, path.safRoot());
        if (currNode == null)
            return false;

        String[] nodes = path.makeRelativePathNodes();
        for (String nodeName : nodes) {
            DocumentFile f = currNode.findFile(nodeName);
            if (f == null || !f.exists())
                return false;

            currNode = f;
        }
        CACHE.put(cacheKey, currNode);

        return true;
    }

    @TargetApi(21)
    public Uri makeSafRootDir(@NonNull Uri dir)
    {
        return DocumentsContract.buildDocumentUriUsingTree(dir,
                DocumentsContract.getTreeDocumentId(dir));
    }

    /*
     * Returns the file native descriptor
     * (for reading (mode='r'), writing (mode='w') or
     * reading and writing (mode='rw') by the given name
     */

    @TargetApi(21)
    public int openFD(@NonNull Uri dir,
                      @NonNull String fileName,
                      @NonNull String mode)
    {
        Uri filePath = getFileUri(dir, fileName, true);
        if (filePath == null)
            return -1;

        return openFD(filePath, mode);
    }

    @TargetApi(21)
    public int openFD(@NonNull FakePath path,
                      @NonNull String mode)
    {
        Uri filePath = getFileUri(path, true);
        if (filePath == null)
            return -1;

        return openFD(filePath, mode);
    }

    public int openFD(@NonNull Uri filePath, String mode)
    {
        if (!("r".equals(mode) || "w".equals(mode) || "rw".equals(mode))) {
            Log.e(TAG, "Only r, w or rw modes supported");
            return -1;
        }

        ContentResolver cr = appContext.getContentResolver();
        try (ParcelFileDescriptor fd = cr.openFileDescriptor(filePath, mode)) {
            if (fd == null)
                return -1;

            return fd.detachFd();

        } catch (Throwable e) {
            Log.e(TAG, "Unable to get native fd", e);
            return -1;
        }
    }

    @TargetApi(21)
    @Nullable
    public Stat stat(@NonNull Uri safRoot, @NonNull String fileName)
    {
        String cacheKey = safRoot.toString() + File.separator + fileName;
        DocumentFile f = CACHE.get(cacheKey);
        if (f == null) {
            DocumentFile tree = DocumentFile.fromTreeUri(appContext, safRoot);
            if (tree == null)
                return null;
            f = getFile(tree, fileName, false);
            if (f != null)
                CACHE.put(cacheKey, f);
        }

        return stat(f);
    }

    @TargetApi(21)
    @Nullable
    public Stat statSafRoot(@NonNull Uri safRoot)
    {
        String cacheKey = safRoot.toString();
        DocumentFile tree = CACHE.get(cacheKey);
        if (tree == null) {
            tree = DocumentFile.fromTreeUri(appContext, safRoot);
            if (tree != null)
                CACHE.put(cacheKey, tree);
        }

        return stat(tree);
    }

    @TargetApi(19)
    @Nullable
    public Stat stat(@NonNull Uri filePath)
    {
        String cacheKey = filePath.toString();
        DocumentFile f = CACHE.get(cacheKey);
        if (f == null) {
            f = DocumentFile.fromSingleUri(appContext, filePath);
            if (f != null)
                CACHE.put(cacheKey, f);
        }

        return stat(f);
    }

    @TargetApi(21)
    @Nullable
    public Stat stat(@NonNull FakePath path)
    {
        String cacheKey = path.toString();
        DocumentFile f = CACHE.get(cacheKey);
        if (f == null) {
            f = getFile(path, true);
            if (f != null)
                CACHE.put(cacheKey, f);
        }

        return stat(f);
    }

    private Stat stat(DocumentFile f)
    {
        return (f == null ?
                null :
                new Stat(f.getName(), f.isDirectory(),
                    f.length(), f.lastModified()));
    }

    /*
     * Creates the directory named by this path, including any
     * necessary but nonexistent parent directories.
     * For example, for path='/saf_root/foo/bar'
     * method creates `foo` and `bar` directories.
     * Even if the file has an extension called `foo.txt`,
     * the directory `foo.txt` will be created, not the file
     */

    @TargetApi(21)
    public boolean mkdirs(@NonNull FakePath path)
    {
        String cacheKey = path.toString();
        DocumentFile cached = CACHE.get(cacheKey);
        if (cached != null && cached.exists())
            return true;

        DocumentFile currNode = DocumentFile.fromTreeUri(appContext, path.safRoot());
        if (currNode == null)
            return false;

        String[] nodes = path.makeRelativePathNodes();
        for (String nodeName : nodes) {
            DocumentFile node = currNode.findFile(nodeName);
            if (node == null)
                node = currNode.createDirectory(nodeName);

            if (node == null)
                return false;

            currNode = node;
        }
        CACHE.put(cacheKey, currNode);

        return true;
    }

    @TargetApi(19)
    public Uri getParentDirUri(@NonNull Uri filePath)
    {
        String cacheKey = filePath.toString();
        DocumentFile f = CACHE.get(cacheKey);
        if (f == null) {
            f = DocumentFile.fromSingleUri(appContext, filePath);
            if (f != null)
                CACHE.put(cacheKey, f);
        }
        if (f == null)
            return null;

        DocumentFile parent = f.getParentFile();

        return (parent == null || !parent.isDirectory() ? null : parent.getUri());
    }
}
