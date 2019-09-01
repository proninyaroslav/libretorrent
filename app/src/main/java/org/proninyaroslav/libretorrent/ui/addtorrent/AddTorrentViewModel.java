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

package org.proninyaroslav.libretorrent.ui.addtorrent;

import android.app.Application;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.collection.ArraySet;
import androidx.databinding.Observable;
import androidx.databinding.ObservableField;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.exception.DecodeException;
import org.proninyaroslav.libretorrent.core.exception.FreeSpaceException;
import org.proninyaroslav.libretorrent.core.exception.NoFilesSelectedException;
import org.proninyaroslav.libretorrent.core.model.AddTorrentParams;
import org.proninyaroslav.libretorrent.core.model.TorrentEngine;
import org.proninyaroslav.libretorrent.core.model.data.MagnetInfo;
import org.proninyaroslav.libretorrent.core.model.data.Priority;
import org.proninyaroslav.libretorrent.core.model.data.filetree.BencodeFileTree;
import org.proninyaroslav.libretorrent.core.model.data.filetree.FileNode;
import org.proninyaroslav.libretorrent.core.model.data.metainfo.BencodeFileItem;
import org.proninyaroslav.libretorrent.core.model.data.metainfo.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.core.settings.SettingsManager;
import org.proninyaroslav.libretorrent.core.storage.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.storage.TorrentRepository;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;
import org.proninyaroslav.libretorrent.core.system.filesystem.FileSystemFacade;
import org.proninyaroslav.libretorrent.core.utils.BencodeFileTreeUtils;
import org.proninyaroslav.libretorrent.core.utils.FileTreeDepthFirstSearch;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.subjects.BehaviorSubject;

public class AddTorrentViewModel extends AndroidViewModel
{
    @SuppressWarnings("unused")
    private static final String TAG = AddTorrentViewModel.class.getSimpleName();

    public AddTorrentMutableParams mutableParams = new AddTorrentMutableParams();
    public ObservableField<TorrentMetaInfo> info = new ObservableField<>();
    private MutableLiveData<DecodeState> decodeState = new MutableLiveData<>();
    private TorrentRepository repo;
    private FileSystemFacade fs;
    private TorrentEngine engine;
    private SharedPreferences pref;
    private TorrentDecodeTask decodeTask;
    /* BEP53 standard. Optional field */
    private ArrayList<Priority> magnetPriorities;
    private CompositeDisposable disposable = new CompositeDisposable();

    public BencodeFileTree fileTree;
    public BehaviorSubject<List<BencodeFileTree>> children = BehaviorSubject.create();
    /* Current directory */
    private BencodeFileTree curDir;
    public Throwable errorReport;

    public enum Status
    {
        UNKNOWN,
        DECODE_TORRENT_FILE,
        DECODE_TORRENT_COMPLETED,
        FETCHING_MAGNET,
        FETCHING_HTTP,
        FETCHING_MAGNET_COMPLETED,
        FETCHING_HTTP_COMPLETED,
        ERROR
    }

    public static class DecodeState
    {
        public Status status;
        public Throwable error;

        public DecodeState(Status status, Throwable error)
        {
            this.status = status;
            this.error = error;
        }
        public DecodeState(Status status)
        {
            this(status, null);
        }
    }

    public AddTorrentViewModel(@NonNull Application application)
    {
        super(application);

        repo = RepositoryHelper.getTorrentRepository(application);
        fs = SystemFacadeHelper.getFileSystemFacade(application);
        pref = SettingsManager.getInstance(application).getPreferences();
        engine = TorrentEngine.getInstance(getApplication());

        info.addOnPropertyChangedCallback(infoCallback);
        mutableParams.getDirPath().addOnPropertyChangedCallback(dirPathCallback);
        decodeState.setValue(new DecodeState(Status.UNKNOWN));

        /* Init download dir */
        String path = pref.getString(application.getString(R.string.pref_key_save_torrents_in),
                                     SettingsManager.Default.saveTorrentFilesIn(getApplication()));
        mutableParams.getDirPath().set(Uri.parse(fs.normalizeFileSystemPath(path)));
    }

    public LiveData<DecodeState> getDecodeState()
    {
        return decodeState;
    }

    @Override
    protected void onCleared()
    {
        disposable.clear();
        info.removeOnPropertyChangedCallback(infoCallback);
        mutableParams.getDirPath().removeOnPropertyChangedCallback(dirPathCallback);
    }

    public void startDecodeTask(@NonNull Uri uri)
    {
        /*
         * The AsyncTask class must be loaded on the UI thread. This is done automatically as of JELLY_BEAN.
         * http://developer.android.com/intl/ru/reference/android/os/AsyncTask.html
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            decodeTask = new TorrentDecodeTask(this);
            decodeTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, uri);
        } else {
            Handler handler = new Handler(getApplication().getMainLooper());
            handler.post(() -> {
                decodeTask = new TorrentDecodeTask(this);
                decodeTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, uri);
            });
        }
    }

    private static class TorrentDecodeTask extends AsyncTask<Uri, Void, Throwable>
    {
        private final WeakReference<AddTorrentViewModel> viewModel;

        private TorrentDecodeTask(AddTorrentViewModel viewModel)
        {
            this.viewModel = new WeakReference<>(viewModel);
        }

        @Override
        protected void onPreExecute()
        {
            /* Nothing */
        }

        @Override
        protected Throwable doInBackground(Uri... params)
        {
            AddTorrentViewModel v = viewModel.get();
            if (v == null || isCancelled())
                return null;

            Uri uri = params[0];
            try {
                switch (uri.getScheme()) {
                    case Utils.FILE_PREFIX:
                    case Utils.CONTENT_PREFIX:
                        v.mutableParams.setSource(uri.toString());
                        v.decodeState.postValue(
                                new DecodeState(AddTorrentViewModel.Status.DECODE_TORRENT_FILE));
                        break;
                    case Utils.MAGNET_PREFIX:
                        v.mutableParams.setSource(uri.toString());
                        v.decodeState.postValue(
                                new DecodeState(AddTorrentViewModel.Status.FETCHING_MAGNET));
                        v.mutableParams.setFromMagnet(true);

                        Pair<MagnetInfo, Single<TorrentMetaInfo>> res = v.engine.fetchMagnet(uri.toString());
                        MagnetInfo magnetInfo = res.first;
                        if (magnetInfo != null && !isCancelled()) {
                            v.info.set(new TorrentMetaInfo(magnetInfo.getName(), magnetInfo.getSha1hash()));
                            v.observeFetchedMetadata(res.second);

                            if (magnetInfo.getFilePriorities() != null)
                                v.magnetPriorities = new ArrayList<>(magnetInfo.getFilePriorities());
                        }
                        break;
                    case Utils.HTTP_PREFIX:
                    case Utils.HTTPS_PREFIX:
                        v.decodeState.postValue(new DecodeState(AddTorrentViewModel.Status.FETCHING_HTTP));

                        File httpTmp = v.fs.makeTempFile(".torrent");
                        byte[] response = Utils.fetchHttpUrl(v.getApplication(), uri.toString());
                        org.apache.commons.io.FileUtils.writeByteArrayToFile(httpTmp, response);

                        if (httpTmp.exists() && !isCancelled()) {
                            v.mutableParams.setSource(
                                    v.fs.normalizeFileSystemPath(httpTmp.getAbsolutePath()));
                        } else {
                            return new IllegalArgumentException("Unknown path to the torrent file");
                        }

                        break;
                    default:
                        throw new IllegalArgumentException("Invalid scheme");
                }

                String tmpSource = v.mutableParams.getSource();
                boolean fromMagnet = v.mutableParams.isFromMagnet();
                if (tmpSource != null && !fromMagnet && !isCancelled())
                    readTorrentFile(Uri.parse(tmpSource));

            } catch (Throwable e) {
                return e;
            }

            return null;
        }

        private void readTorrentFile(Uri uri) throws IOException, DecodeException
        {
            AddTorrentViewModel v = viewModel.get();
            if (v == null || isCancelled())
                return;

            ContentResolver contentResolver = v.getApplication().getContentResolver();
            try (ParcelFileDescriptor outPfd = contentResolver.openFileDescriptor(uri, "r")) {
                FileDescriptor outFd = outPfd.getFileDescriptor();

                try (FileInputStream is = new FileInputStream(outFd)) {
                    v.info.set(new TorrentMetaInfo(is));
                }
            } catch (FileNotFoundException e) {
                throw new FileNotFoundException(uri.toString()  + ": " + e.getMessage());
            }
        }

        @Override
        protected void onPostExecute(Throwable e)
        {
            AddTorrentViewModel v = viewModel.get();
            if (v == null || isCancelled())
                return;

            if (e != null) {
                v.decodeState.postValue(new DecodeState(AddTorrentViewModel.Status.ERROR, e));
                return;
            }

            DecodeState prevState = v.decodeState.getValue();
            if (prevState == null)
                return;

            switch (prevState.status) {
                case DECODE_TORRENT_FILE:
                    v.decodeState.postValue(
                            new DecodeState(AddTorrentViewModel.Status.DECODE_TORRENT_COMPLETED));
                    break;
                case FETCHING_HTTP:
                    v.decodeState.postValue(
                            new DecodeState(AddTorrentViewModel.Status.FETCHING_HTTP_COMPLETED));
                    break;
            }
        }
    }

    private void observeFetchedMetadata(Single<TorrentMetaInfo> single)
    {
        disposable.add(single
                .subscribe((downloadInfo) -> {
                    info.set(downloadInfo);
                    decodeState.postValue(
                            new DecodeState(AddTorrentViewModel.Status.FETCHING_MAGNET_COMPLETED));
                }));
    }

    private Observable.OnPropertyChangedCallback infoCallback = new Observable.OnPropertyChangedCallback()
    {
        @Override
        public void onPropertyChanged(Observable sender, int propertyId)
        {
            TorrentMetaInfo downloadInfo = info.get();
            if (downloadInfo == null)
                return;

            String name = mutableParams.getName();
            if (name == null || name.equals(downloadInfo.sha1Hash))
                mutableParams.setName(downloadInfo.torrentName);
        }
    };

    private Observable.OnPropertyChangedCallback dirPathCallback = new Observable.OnPropertyChangedCallback()
    {
        @Override
        public void onPropertyChanged(Observable sender, int propertyId)
        {
            Uri dirPath = mutableParams.getDirPath().get();
            if (dirPath == null)
                return;

            mutableParams.setStorageFreeSpace(fs.getDirAvailableBytes(dirPath));
            mutableParams.setDirName(fs.getDirName(dirPath));
        }
    };

    public void makeFileTree()
    {
        if (fileTree != null)
            return;

        TorrentMetaInfo infoObj = info.get();
        if (infoObj == null)
            return;
        List<BencodeFileItem> files = infoObj.fileList;
        if (files.isEmpty())
            return;

        fileTree = BencodeFileTreeUtils.buildFileTree(files);

        if (magnetPriorities == null || magnetPriorities.size() == 0) {
            fileTree.select(true);

        } else {
            FileTreeDepthFirstSearch<BencodeFileTree> search = new FileTreeDepthFirstSearch<>();
            /* Select files that have non-IGNORE priority (see BEP35 standard) */
            long n = (magnetPriorities.size() > files.size() ? files.size() : magnetPriorities.size());
            for (int i = 0; i < n; i++) {
                if (magnetPriorities.get(i) == Priority.IGNORE)
                    continue;
                BencodeFileTree file = search.find(fileTree, i);
                if (file != null)
                    file.select(true);
            }
        }

        /* Is assigned the root dir of the file tree */
        updateCurDir(fileTree);
    }

    /*
     * Navigate back to an upper directory.
     */

    public void upToParentDirectory()
    {
        updateCurDir(curDir.getParent());
    }

    public List<BencodeFileTree> getChildren(BencodeFileTree node)
    {
        List<BencodeFileTree> children = new ArrayList<>();
        if (node == null || node.isFile())
            return children;

        /* Adding parent dir for navigation */
        if (curDir != fileTree && curDir.getParent() != null)
            children.add(0, new BencodeFileTree(BencodeFileTree.PARENT_DIR, 0L, FileNode.Type.DIR, curDir.getParent()));

        children.addAll(curDir.getChildren());

        return children;
    }

    public void chooseDirectory(@NonNull String name)
    {
        BencodeFileTree node = curDir.getChild(name);
        if (node == null)
            return;

        if (node.isFile())
            node = fileTree;

        updateCurDir(node);
    }

    public void selectFile(@NonNull String name, boolean selected)
    {
        BencodeFileTree node = curDir.getChild(name);
        if (node == null)
            return;

        node.select(selected);
    }


    private void updateCurDir(BencodeFileTree node)
    {
        curDir = node;
        children.onNext(getChildren(curDir));
    }

    private Set<Integer> getSelectedFileIndexes()
    {
        if (fileTree == null)
            return new HashSet<>();

        List<BencodeFileTree> files = BencodeFileTreeUtils.getFiles(fileTree);
        Set<Integer> indexes = new ArraySet<>();
        for (BencodeFileTree file : files)
            if (file.isSelected())
                indexes.add(file.getIndex());

        return indexes;
    }

    public boolean addTorrent() throws Exception
    {
        TorrentMetaInfo downloadInfo = info.get();
        if (downloadInfo == null)
            return false;

        boolean fromMagnet = mutableParams.isFromMagnet();

        String source = mutableParams.getSource();
        if (source == null)
            return false;

        Uri dirPath = mutableParams.getDirPath().get();
        if (dirPath == null)
            return false;

        String name = mutableParams.getName();
        if (TextUtils.isEmpty(name))
            return false;

        Set<Integer> selectedFiles = getSelectedFileIndexes();
        if (!fromMagnet) {
            if (selectedFiles.isEmpty())
                throw new NoFilesSelectedException();

            if (!checkFreeSpace())
                throw new FreeSpaceException();
        }

        Priority[] priorities = new Priority[downloadInfo.fileCount];
        if (downloadInfo.fileCount != 0) {
            if (selectedFiles.size() == downloadInfo.fileCount) {
                Arrays.fill(priorities, Priority.DEFAULT);
            } else {
                Arrays.fill(priorities, Priority.IGNORE);
                for (int index : selectedFiles)
                    priorities[index] = Priority.DEFAULT;
            }
        }

        AddTorrentParams params = new AddTorrentParams(source, fromMagnet,downloadInfo.sha1Hash,
                name, priorities, dirPath,
                mutableParams.isSequentialDownload(),
                !mutableParams.isStartAfterAdd());

        /* TODO: maybe rewrite to WorkManager */
        /* Sync wait inserting */
        Exception[] err = new Exception[1];
        try {
            Thread t = new Thread(() ->  {
                try {
                    engine.addTorrentSync(params, false);

                } catch (Exception e) {
                    err[0] = e;
                }
            });
            t.start();
            t.join();

        } catch (InterruptedException e) {
            return false;
        }

        if (err[0] != null)
            throw err[0];

        return true;
    }

    private boolean checkFreeSpace()
    {
        if (fileTree == null)
            return false;

        long storageFreeSpace = mutableParams.getStorageFreeSpace();

        return storageFreeSpace == -1 || storageFreeSpace >= fileTree.selectedFileSize();
    }

    public void finish()
    {
        if (decodeTask != null)
            decodeTask.cancel(true);

        cancelFetchMagnet();
    }

    private void cancelFetchMagnet()
    {
        TorrentMetaInfo infoVal = info.get();
        if (infoVal == null)
            return;

        DecodeState state = decodeState.getValue();
        if (state != null && state.status == Status.FETCHING_MAGNET)
            engine.cancelFetchMagnet(infoVal.sha1Hash);
    }
}
