/*
 * Copyright (C) 2019-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.collection.ArraySet;
import androidx.core.util.Pair;
import androidx.databinding.Observable;
import androidx.databinding.ObservableField;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.apache.commons.io.FileUtils;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.TaskRunner;
import org.proninyaroslav.libretorrent.core.exception.DecodeException;
import org.proninyaroslav.libretorrent.core.exception.FreeSpaceException;
import org.proninyaroslav.libretorrent.core.exception.NoFilesSelectedException;
import org.proninyaroslav.libretorrent.core.exception.UnknownUriException;
import org.proninyaroslav.libretorrent.core.model.AddTorrentParams;
import org.proninyaroslav.libretorrent.core.model.TorrentEngine;
import org.proninyaroslav.libretorrent.core.model.data.Priority;
import org.proninyaroslav.libretorrent.core.model.data.entity.TagInfo;
import org.proninyaroslav.libretorrent.core.model.data.metainfo.BencodeFileItem;
import org.proninyaroslav.libretorrent.core.model.data.metainfo.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.core.model.filetree.BencodeFileTree;
import org.proninyaroslav.libretorrent.core.model.filetree.FileNode;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.core.system.FileSystemFacade;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;
import org.proninyaroslav.libretorrent.core.utils.BencodeFileTreeUtils;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;

public class AddTorrentViewModel extends AndroidViewModel {
    private static final String TAG = AddTorrentViewModel.class.getSimpleName();

    public AddTorrentMutableParams mutableParams = new AddTorrentMutableParams();
    public ObservableField<TorrentMetaInfo> info = new ObservableField<>();
    private final MutableLiveData<DecodeState> decodeState = new MutableLiveData<>();
    private final FileSystemFacade fs;
    private final TorrentEngine engine;
    private Future<?> decodeTaskFuture;
    /* BEP53 standard. Optional field */
    private ArrayList<Priority> magnetPriorities;
    private final CompositeDisposable disposable = new CompositeDisposable();
    private Disposable observeEngineRunning;

    public BencodeFileTree fileTree;
    private BencodeFileTree[] treeLeaves;
    public BehaviorSubject<List<BencodeFileTree>> children = BehaviorSubject.create();
    /* Current directory */
    private BencodeFileTree curDir;

    private final ArrayList<TagInfo> tags = new ArrayList<>();
    private final BehaviorSubject<List<TagInfo>> tagsSubject = BehaviorSubject.createDefault(tags);

    public enum Status {
        UNKNOWN,
        DECODE_TORRENT_FILE,
        DECODE_TORRENT_COMPLETED,
        FETCHING_MAGNET,
        FETCHING_HTTP,
        FETCHING_MAGNET_COMPLETED,
        FETCHING_HTTP_COMPLETED,
        ERROR
    }

    public static class DecodeState {
        public Status status;
        public Throwable error;

        public DecodeState(Status status, Throwable error) {
            this.status = status;
            this.error = error;
        }

        public DecodeState(Status status) {
            this(status, null);
        }
    }

    public AddTorrentViewModel(@NonNull Application application) {
        super(application);

        fs = SystemFacadeHelper.getFileSystemFacade(application);
        SettingsRepository pref = RepositoryHelper.getSettingsRepository(application);
        engine = TorrentEngine.getInstance(getApplication());

        disposable.add(engine.observeNeedStartEngine()
                .subscribeOn(Schedulers.io())
                .filter((needStart) -> needStart)
                .subscribe((needStart) -> engine.start()));

        info.addOnPropertyChangedCallback(infoCallback);
        mutableParams.getDirPath().addOnPropertyChangedCallback(dirPathCallback);
        decodeState.setValue(new DecodeState(Status.UNKNOWN));

        /* Init download dir */
        String path = pref.saveTorrentsIn();
        mutableParams.getDirPath().set(Uri.parse(path));
    }

    public List<TagInfo> getCurrentTorrentTags() {
        return tags;
    }

    public io.reactivex.Observable<List<TagInfo>> observeTags() {
        return tagsSubject;
    }

    public void addTorrentTag(@NonNull TagInfo info) {
        tags.add(info);
        tagsSubject.onNext(tags);
    }

    public void removeTorrentTag(@NonNull TagInfo info) {
        tags.remove(info);
        tagsSubject.onNext(tags);
    }

    public LiveData<DecodeState> getDecodeState() {
        return decodeState;
    }

    @Override
    protected void onCleared() {
        finish();
        disposable.clear();
        info.removeOnPropertyChangedCallback(infoCallback);
        mutableParams.getDirPath().removeOnPropertyChangedCallback(dirPathCallback);
    }

    public void startDecode(@NonNull Uri uri) {
        if (observeEngineRunning != null && observeEngineRunning.isDisposed()) {
            return;
        }

        observeEngineRunning = engine.observeEngineRunning()
                .subscribeOn(Schedulers.io())
                .subscribe((isRunning) -> {
                    if (isRunning) {
                        startDecodeTask(uri);
                        if (observeEngineRunning != null) {
                            observeEngineRunning.dispose();
                        }
                    }
                });
    }

    public void startDecodeTask(Uri uri) {
        var runner = new TaskRunner();
        decodeTaskFuture = runner.executeAsync(new TorrentDecodeTask(uri), (throwable) -> {
            if (throwable != null) {
                decodeState.postValue(new DecodeState(Status.ERROR, throwable));
                return;
            }

            DecodeState prevState = decodeState.getValue();
            if (prevState == null) {
                return;
            }

            switch (prevState.status) {
                case DECODE_TORRENT_FILE -> decodeState.postValue(
                        new DecodeState(Status.DECODE_TORRENT_COMPLETED));
                case FETCHING_HTTP -> decodeState.postValue(
                        new DecodeState(Status.FETCHING_HTTP_COMPLETED));
                default -> {
                }
            }
        });
    }

    private class TorrentDecodeTask implements Callable<Throwable> {
        private final Uri uri;

        private TorrentDecodeTask(Uri uri) {
            this.uri = uri;
        }

        @Override
        public Throwable call() {
            if (Thread.interrupted()) {
                return null;
            }

            try {
                switch (Objects.requireNonNull(uri.getScheme())) {
                    case Utils.FILE_PREFIX, Utils.CONTENT_PREFIX -> {
                        mutableParams.setSource(uri.toString());
                        decodeState.postValue(new DecodeState(Status.DECODE_TORRENT_FILE));
                    }
                    case Utils.MAGNET_PREFIX -> {
                        mutableParams.setSource(uri.toString());
                        decodeState.postValue(new DecodeState(Status.FETCHING_MAGNET));
                        mutableParams.setFromMagnet(true);

                        var res = engine.fetchMagnet(uri.toString());
                        if (res != null) {
                            var magnetInfo = res.first;
                            if (magnetInfo != null && !Thread.interrupted()) {
                                info.set(new TorrentMetaInfo(magnetInfo.getName(), magnetInfo.getSha1hash()));
                                observeFetchedMetadata(res.second);
                                if (magnetInfo.getFilePriorities() != null) {
                                    magnetPriorities = new ArrayList<>(magnetInfo.getFilePriorities());
                                }
                            }
                        }
                    }
                    case Utils.HTTP_PREFIX, Utils.HTTPS_PREFIX -> {
                        decodeState.postValue(new DecodeState(Status.FETCHING_HTTP));

                        var httpTmp = fs.makeTempFile(".torrent");
                        var response = Utils.fetchHttpUrl(getApplication(), uri.toString());
                        FileUtils.writeByteArrayToFile(httpTmp, response);

                        if (httpTmp.exists() && !Thread.interrupted()) {
                            mutableParams.setSource(
                                    fs.normalizeFileSystemPath(httpTmp.getAbsolutePath()));
                        } else {
                            return new IllegalArgumentException("Unknown path to the torrent file");
                        }
                    }
                    default -> throw new IllegalArgumentException("Invalid scheme");
                }

                String tmpSource = mutableParams.getSource();
                boolean fromMagnet = mutableParams.isFromMagnet();
                if (tmpSource != null && !fromMagnet && !Thread.interrupted()) {
                    readTorrentFile(Uri.parse(tmpSource));
                }

            } catch (Throwable e) {
                return e;
            }

            return null;
        }

        private void readTorrentFile(Uri uri) throws IOException, DecodeException {
            var contentResolver = getApplication().getContentResolver();
            try (var outPfd = contentResolver.openFileDescriptor(uri, "r")) {
                if (outPfd == null) {
                    throw new IOException("ParcelFileDescriptor is null");
                }
                var outFd = outPfd.getFileDescriptor();
                try (FileInputStream is = new FileInputStream(outFd)) {
                    info.set(new TorrentMetaInfo(is));
                }
            } catch (FileNotFoundException e) {
                throw new FileNotFoundException(uri + ": " + e.getMessage());
            }
        }
    }

    private void observeFetchedMetadata(Single<TorrentMetaInfo> single) {
        disposable.add(single.subscribe(
                (downloadInfo) -> {
                    info.set(downloadInfo);
                    decodeState.postValue(new DecodeState(Status.FETCHING_MAGNET_COMPLETED));
                },
                (e) -> decodeState.postValue(new DecodeState(Status.ERROR, e))));
    }

    private final Observable.OnPropertyChangedCallback infoCallback = new Observable.OnPropertyChangedCallback() {
        @Override
        public void onPropertyChanged(Observable sender, int propertyId) {
            TorrentMetaInfo downloadInfo = info.get();
            if (downloadInfo == null) {
                return;
            }

            mutableParams.setName(downloadInfo.torrentName);
        }
    };

    private final Observable.OnPropertyChangedCallback dirPathCallback = new Observable.OnPropertyChangedCallback() {
        @Override
        public void onPropertyChanged(Observable sender, int propertyId) {
            Uri dirPath = mutableParams.getDirPath().get();
            if (dirPath == null) {
                return;
            }

            disposable.add(Completable.fromRunnable(() -> {
                        try {
                            mutableParams.setStorageFreeSpace(fs.getDirAvailableBytes(dirPath));
                            mutableParams.setDirName(fs.getDirPath(dirPath));
                        } catch (UnknownUriException e) {
                            Log.e(TAG, Log.getStackTraceString(e));
                        }
                    })
                    .subscribeOn(Schedulers.io())
                    .subscribe());
        }
    };

    public void makeFileTree() {
        if (fileTree != null) {
            return;
        }

        TorrentMetaInfo infoObj = info.get();
        if (infoObj == null) {
            return;
        }
        List<BencodeFileItem> files = infoObj.fileList;
        if (files.isEmpty()) {
            return;
        }

        Pair<BencodeFileTree, BencodeFileTree[]> res = BencodeFileTreeUtils.buildFileTree(files);
        this.fileTree = res.first;
        this.treeLeaves = res.second;

        if (magnetPriorities == null || magnetPriorities.isEmpty()) {
            fileTree.select(true, false);
        } else {
            /* Select files that have non-IGNORE priority (see BEP35 standard) */
            for (int i = 0; i < files.size(); i++) {
                BencodeFileTree file = treeLeaves[i];
                if (file != null) {
                    Priority p = (i >= magnetPriorities.size() ?
                            Priority.IGNORE :
                            magnetPriorities.get(i));
                    file.select(p != Priority.IGNORE, false);
                }
            }
        }

        /* Is assigned the root dir of the file tree */
        updateCurDir(fileTree);
    }

    /*
     * Navigate back to an upper directory.
     */

    public void upToParentDirectory() {
        updateCurDir(curDir.getParent());
    }

    public List<BencodeFileTree> getChildren(BencodeFileTree node) {
        List<BencodeFileTree> children = new ArrayList<>();
        if (node == null || node.isFile()) {
            return children;
        }

        /* Adding parent dir for navigation */
        if (curDir != fileTree && curDir.getParent() != null) {
            children.add(0, new BencodeFileTree(BencodeFileTree.PARENT_DIR, 0L, FileNode.Type.DIR, curDir.getParent()));
        }

        children.addAll(curDir.getChildren());

        return children;
    }

    public void chooseDirectory(@NonNull String name) {
        BencodeFileTree node = curDir.getChild(name);
        if (node == null) {
            return;
        }

        if (node.isFile()) {
            node = fileTree;
        }

        updateCurDir(node);
    }

    public void selectFile(@NonNull String name, boolean selected) {
        BencodeFileTree node = curDir.getChild(name);
        if (node == null) {
            return;
        }

        node.select(selected, true);
        updateChildren();
    }

    private void updateCurDir(BencodeFileTree node) {
        curDir = node;
        updateChildren();
    }

    private void updateChildren() {
        children.onNext(getChildren(curDir));
    }

    private Set<Integer> getSelectedFileIndexes() {
        if (fileTree == null || treeLeaves == null)
            return new HashSet<>();

        Set<Integer> indexes = new ArraySet<>();
        for (BencodeFileTree file : treeLeaves)
            if (file.isSelected())
                indexes.add(file.getIndex());

        return indexes;
    }

    public boolean addTorrent() throws Exception {
        TorrentMetaInfo downloadInfo = info.get();
        if (downloadInfo == null) {
            return false;
        }

        boolean fromMagnet = mutableParams.isFromMagnet();
        DecodeState state = decodeState.getValue();

        String source = mutableParams.getSource();
        if (source == null) {
            return false;
        }

        Uri dirPath = mutableParams.getDirPath().get();
        if (dirPath == null)
            return false;

        String name = mutableParams.getName();
        if (TextUtils.isEmpty(name)) {
            return false;
        }

        boolean ignoreFreeSpace = mutableParams.isIgnoreFreeSpace();
        Set<Integer> selectedFiles = getSelectedFileIndexes();
        if (!ignoreFreeSpace && state != null &&
                (state.status == Status.DECODE_TORRENT_COMPLETED ||
                        state.status == Status.FETCHING_MAGNET_COMPLETED ||
                        state.status == Status.FETCHING_HTTP_COMPLETED)) {
            if (selectedFiles.isEmpty()) {
                throw new NoFilesSelectedException();
            }

            if (!checkFreeSpace()) {
                throw new FreeSpaceException();
            }
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

        AddTorrentParams params = new AddTorrentParams(
                source,
                fromMagnet,
                downloadInfo.sha1Hash,
                name,
                priorities,
                dirPath,
                mutableParams.isSequentialDownload(),
                !mutableParams.isStartAfterAdd(),
                tags,
                mutableParams.isFirstLastPiecePriority()
        );

        /* TODO: maybe rewrite to WorkManager or Kotlin Coroutines */
        /* Sync wait inserting */
        Exception[] err = new Exception[1];
        try {
            Thread t = new Thread(() -> {
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

        if (err[0] != null) {
            throw err[0];
        }

        return true;
    }

    private boolean checkFreeSpace() {
        if (fileTree == null) {
            return false;
        }

        long storageFreeSpace = mutableParams.getStorageFreeSpace();
        long treeFreeSpace = fileTree.selectedFileSize();

        return storageFreeSpace == -1 || storageFreeSpace >= treeFreeSpace;
    }

    public void finish() {
        if (decodeTaskFuture != null) {
            decodeTaskFuture.cancel(true);
        }

        cancelFetchMagnet();
    }

    private void cancelFetchMagnet() {
        TorrentMetaInfo infoVal = info.get();
        if (infoVal == null) {
            return;
        }

        DecodeState state = decodeState.getValue();
        if (state != null && state.status == Status.FETCHING_MAGNET) {
            engine.cancelFetchMagnet(infoVal.sha1Hash);
        }
    }
}
