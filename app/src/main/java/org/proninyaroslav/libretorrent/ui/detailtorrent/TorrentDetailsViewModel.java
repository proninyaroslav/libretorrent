/*
 * Copyright (C) 2019-2022 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.detailtorrent;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.core.util.Pair;
import androidx.databinding.Observable;
import androidx.databinding.library.baseAdapters.BR;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;
import androidx.lifecycle.viewmodel.ViewModelInitializer;

import org.proninyaroslav.libretorrent.MainApplication;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.exception.UnknownUriException;
import org.proninyaroslav.libretorrent.core.model.TorrentEngine;
import org.proninyaroslav.libretorrent.core.model.TorrentInfoProvider;
import org.proninyaroslav.libretorrent.core.model.data.AdvancedTorrentInfo;
import org.proninyaroslav.libretorrent.core.model.data.PeerInfo;
import org.proninyaroslav.libretorrent.core.model.data.Priority;
import org.proninyaroslav.libretorrent.core.model.data.TorrentInfo;
import org.proninyaroslav.libretorrent.core.model.data.TrackerInfo;
import org.proninyaroslav.libretorrent.core.model.data.entity.TagInfo;
import org.proninyaroslav.libretorrent.core.model.data.entity.Torrent;
import org.proninyaroslav.libretorrent.core.model.data.metainfo.BencodeFileItem;
import org.proninyaroslav.libretorrent.core.model.data.metainfo.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.core.model.filetree.FileNode;
import org.proninyaroslav.libretorrent.core.model.filetree.FilePriority;
import org.proninyaroslav.libretorrent.core.model.filetree.TorrentContentFileTree;
import org.proninyaroslav.libretorrent.core.model.stream.TorrentStreamServer;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.core.storage.TagRepository;
import org.proninyaroslav.libretorrent.core.storage.TorrentRepository;
import org.proninyaroslav.libretorrent.core.system.FileSystemFacade;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;
import org.proninyaroslav.libretorrent.core.utils.TorrentContentFileTreeUtils;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;

public class TorrentDetailsViewModel extends ViewModel {
    private static final String TAG = TorrentDetailsViewModel.class.getSimpleName();

    public static final CreationExtras.Key<String> KEY_TORRENT_ID = new CreationExtras.Key<>() {
    };

    private final MainApplication application;
    private final String torrentId;
    private final TorrentInfoProvider infoProvider;
    private final TorrentEngine engine;
    private final TorrentRepository repo;
    private final FileSystemFacade fs;
    private final SettingsRepository pref;
    private final CompositeDisposable disposable = new CompositeDisposable();
    public TorrentDetailsInfo info = new TorrentDetailsInfo();
    public TorrentDetailsMutableParams mutableParams = new TorrentDetailsMutableParams();
    private final PublishSubject<Boolean> freeSpaceError = PublishSubject.create();
    private final TagRepository tagRepo;

    private final ReentrantLock syncBuildFileTree = new ReentrantLock();
    public TorrentContentFileTree fileTree;
    private TorrentContentFileTree[] treeLeaves;
    private final BehaviorSubject<List<TorrentContentFileTree>> children = BehaviorSubject.create();
    /* Current directory */
    private TorrentContentFileTree curDir;

    static final ViewModelInitializer<TorrentDetailsViewModel> initializer = new ViewModelInitializer<>(
            TorrentDetailsViewModel.class,
            creationExtras -> {
                var app = (MainApplication) creationExtras.get(
                        ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY
                );
                var torrentId = creationExtras.get(KEY_TORRENT_ID);
                if (app == null || torrentId == null) {
                    throw new IllegalStateException();
                }
                return new TorrentDetailsViewModel(app, torrentId);
            }
    );

    private TorrentDetailsViewModel(MainApplication application, String torrentId) {
        this.application = application;
        this.torrentId = torrentId;
        infoProvider = TorrentInfoProvider.getInstance(application);
        engine = TorrentEngine.getInstance(application);
        repo = RepositoryHelper.getTorrentRepository(application);
        fs = SystemFacadeHelper.getFileSystemFacade(application);
        pref = RepositoryHelper.getSettingsRepository(application);
        mutableParams.addOnPropertyChangedCallback(mutableParamsCallback);
        info.addOnPropertyChangedCallback(infoCallback);
        tagRepo = RepositoryHelper.getTagRepository(application);
    }

    @Override
    protected void onCleared() {
        super.onCleared();

        disposable.clear();
        mutableParams.removeOnPropertyChangedCallback(mutableParamsCallback);
        info.removeOnPropertyChangedCallback(infoCallback);
    }

    public Single<List<TagInfo>> getTags() {
        return tagRepo.getByTorrentIdAsync(torrentId);
    }

    public Flowable<List<TagInfo>> observeTags() {
        return tagRepo.observeByTorrentId(torrentId);
    }

    public Completable addTag(@NonNull TagInfo info) {
        return Completable.fromRunnable(() -> repo.addTag(torrentId, info));
    }

    public Completable removeTag(@NonNull TagInfo info) {
        return Completable.fromRunnable(() -> repo.deleteTag(torrentId, info));
    }

    public io.reactivex.Observable<Boolean> observeFreeSpaceError() {
        return freeSpaceError;
    }

    public io.reactivex.Observable<List<TorrentContentFileTree>> getDirChildren() {
        return children;
    }

    public Flowable<TorrentInfo> observeTorrentInfo() {
        return infoProvider.observeInfo(torrentId);
    }

    public Flowable<AdvancedTorrentInfo> observeAdvancedTorrentInfo() {
        return infoProvider.observeAdvancedInfo(torrentId);
    }

    public Flowable<Torrent> observeTorrent() {
        return repo.observeTorrentById(torrentId);
    }

    public Flowable<List<TrackerInfo>> observeTrackers() {
        return infoProvider.observeTrackersInfo(torrentId);
    }

    public Flowable<List<PeerInfo>> observePeers() {
        return infoProvider.observePeersInfo(torrentId);
    }

    public Flowable<boolean[]> observePieces() {
        return infoProvider.observePiecesInfo(torrentId);
    }

    public Flowable<Pair<Torrent, TorrentInfo>> observeTorrentInfoPair() {
        return Flowable.combineLatest(observeTorrent(), observeTorrentInfo(), Pair::create);
    }

    public Flowable<TorrentMetaInfo> observeTorrentMetaInfo() {
        return engine.observeTorrentMetaInfo(torrentId);
    }

    public void updateInfo(TorrentMetaInfo metaInfo) {
        info.setMetaInfo(metaInfo);

        if (fileTree == null)
            startMakeFileTree();
    }

    public void updateInfo(Torrent torrent, TorrentInfo ti) {
        boolean firstUpdate = info.getTorrent() == null;

        info.setTorrent(torrent);
        info.setTorrentInfo(ti);

        if (firstUpdate)
            initMutableParams();
        if (fileTree == null)
            startMakeFileTree();
    }

    public void updateInfo(AdvancedTorrentInfo advancedInfo) {
        info.setAdvancedInfo(advancedInfo);
    }

    /*
     * Navigate back to an upper directory.
     */

    public void upToParentDirectory() {
        updateCurDir(curDir.getParent());
    }

    public List<TorrentContentFileTree> getChildren(TorrentContentFileTree node) {
        List<TorrentContentFileTree> children = new ArrayList<>();
        if (node == null || node.isFile())
            return children;

        /* Adding parent dir for navigation */
        if (curDir != fileTree && curDir.getParent() != null)
            children.add(0, new TorrentContentFileTree(TorrentContentFileTree.PARENT_DIR, 0L,
                    FileNode.Type.DIR, curDir.getParent()));

        children.addAll(curDir.getChildren());

        return children;
    }

    public void chooseDirectory(@NonNull String name) {
        TorrentContentFileTree node = curDir.getChild(name);
        if (node == null)
            return;

        if (node.isFile())
            node = fileTree;

        updateCurDir(node);
    }

    public boolean isFile(@NonNull String name) {
        TorrentContentFileTree node = curDir.getChild(name);

        return node != null && node.isFile();
    }

    public Single<Uri> getFilePath(@NonNull String name) {
        Context context = application.getApplicationContext();
        TorrentContentFileTree node = curDir.getChild(name);
        if (node == null)
            return Single.error(new NullPointerException("node is null"));

        String relativePath = node.getPath();

        Torrent torrent = info.getTorrent();
        if (torrent == null)
            return Single.error(new NullPointerException("torrent is null"));

        return Single.fromCallable(() -> {
            Uri path = fs.getFileUri(relativePath, torrent.downloadPath);
            if (path == null)
                throw new FileNotFoundException(torrent.downloadPath + relativePath);

            if (Utils.isFileSystemPath(path))
                path = FileProvider.getUriForFile(context,
                        context.getPackageName() + ".provider",
                        new File(path.getPath()));

            return path;
        });
    }

    /*
     * Compare the array with randomly selected priority.
     * If some elements not equals with this priority,
     * set priority type as MIXED, else return randomly selected priority
     */

    @NonNull
    public FilePriority getFilesPriority(@NonNull List<String> fileNames) {
        List<FilePriority> priorities = new ArrayList<>();
        for (String name : fileNames) {
            TorrentContentFileTree file = curDir.getChild(name);
            if (file != null) {
                priorities.add(file.getFilePriority());
            }
        }
        if (priorities.isEmpty()) {
            return new FilePriority(FilePriority.Type.MIXED);
        }

        FilePriority randomPriority = priorities.get(new Random().nextInt(priorities.size()));
        boolean isMixedPriority = false;

        if (randomPriority != null && randomPriority.getType() == FilePriority.Type.MIXED) {
            isMixedPriority = true;

        } else {
            for (FilePriority priority : priorities) {
                if (randomPriority != null && !randomPriority.equals(priority)) {
                    isMixedPriority = true;
                    break;
                }
            }
        }

        if (randomPriority != null && !isMixedPriority) {
            return randomPriority;
        } else {
            return new FilePriority(FilePriority.Type.MIXED);
        }
    }

    public void applyPriority(@NonNull List<String> fileNames,
                              @NonNull FilePriority priority) {
        disposable.add(io.reactivex.Observable.fromIterable(fileNames)
                .map((fileName) -> curDir.getChild(fileName))
                .filter(Objects::nonNull)
                .subscribe((file) -> {
                    file.setPriority(priority, true);
                    updateChildren();
                    mutableParams.setPrioritiesChanged(true);
                }));
    }

    public String getStreamUrl(int fileIndex) {
        String hostname = pref.streamingHostname();
        int port = pref.streamingPort();

        return TorrentStreamServer.makeStreamUrl(hostname, port, torrentId, fileIndex);
    }

    public void deleteTrackers(@NonNull List<String> urls) {
        engine.deleteTrackers(torrentId, urls);
    }

    public void replaceTrackers(@NonNull List<String> urls) {
        engine.replaceTrackers(torrentId, urls);
    }

    public void addTrackers(@NonNull List<String> urls) {
        engine.addTrackers(torrentId, urls);
    }

    public void pauseResumeTorrent() {
        engine.pauseResumeTorrent(torrentId);
    }

    public void deleteTorrent(boolean withFiles) {
        engine.deleteTorrents(Collections.singletonList(torrentId), withFiles);
    }

    public void forceRecheckTorrent() {
        engine.forceRecheckTorrents(Collections.singletonList(torrentId));
    }

    public void forceAnnounceTorrent() {
        engine.forceAnnounceTorrents(Collections.singletonList(torrentId));
    }

    public int getUploadSpeedLimit() {
        return engine.getUploadSpeedLimit(torrentId);
    }

    public int getDownloadSpeedLimit() {
        return engine.getDownloadSpeedLimit(torrentId);
    }

    public String makeMagnet(boolean includePriorities) {
        return engine.makeMagnet(torrentId, includePriorities);
    }

    public void copyTorrentFile(@NonNull Uri destFile) throws IOException, UnknownUriException {
        byte[] bencode = engine.getBencode(torrentId);
        if (bencode == null)
            throw new IOException("Cannot read bencode");

        fs.write(bencode, destFile);
    }

    public void setSpeedLimit(int uploadSpeedLimit, int downloadSpeedLimit) {
        engine.setUploadSpeedLimit(torrentId, uploadSpeedLimit);
        engine.setDownloadSpeedLimit(torrentId, downloadSpeedLimit);
    }

    public Intent makeOpenFileIntent(@NonNull String fileName, @NonNull Uri path) {
        String extension = fs.getExtension(fileName);
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        /* If MIME type is unknown, give user a choice than to open file */
        if (mimeType == null)
            mimeType = "*/*";

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(path, mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        return Intent.createChooser(intent, application.getString(R.string.open_using));
    }

    private Priority[] getFilePriorities() {
        if (treeLeaves == null)
            return null;

        Priority[] priorities = new Priority[treeLeaves.length];
        for (TorrentContentFileTree file : treeLeaves)
            if (file != null && (file.getIndex() >= 0 && file.getIndex() < treeLeaves.length))
                priorities[file.getIndex()] = file.getFilePriority().getPriority();

        return priorities;
    }

    private boolean checkFreeSpace() {
        long storageFreeSpace = info.getStorageFreeSpace();

        return storageFreeSpace == -1 || storageFreeSpace >= fileTree.nonIgnoreFileSize();
    }

    private void initMutableParams() {
        var torrent = info.getTorrent();
        if (torrent == null) {
            return;
        }

        mutableParams.setName(torrent.name);
        mutableParams.setDirPath(torrent.downloadPath);
        mutableParams.setSequentialDownload(engine.isSequentialDownload(torrentId));
        mutableParams.setFirstLastPiecePriority(engine.isFirstLastPiecePriority(torrentId));
    }

    private final Observable.OnPropertyChangedCallback mutableParamsCallback = new Observable.OnPropertyChangedCallback() {
        @Override
        public void onPropertyChanged(Observable sender, int propertyId) {
            if (propertyId == BR.dirPath) {
                Uri dirPath = mutableParams.getDirPath();
                if (dirPath == null)
                    return;

                disposable.add(Completable.fromRunnable(() -> {
                            try {
                                info.setStorageFreeSpace(fs.getDirAvailableBytes(dirPath));
                                info.setDirName(fs.getDirPath(dirPath));
                            } catch (UnknownUriException e) {
                                Log.e(TAG, Log.getStackTraceString(e));
                            }
                        })
                        .subscribeOn(Schedulers.io())
                        .subscribe());
            }

            checkParamsChanged(propertyId);
        }
    };

    private final Observable.OnPropertyChangedCallback infoCallback = new Observable.OnPropertyChangedCallback() {
        @Override
        public void onPropertyChanged(Observable sender, int propertyId) {
            if (propertyId == BR.advancedInfo) {
                AdvancedTorrentInfo advancedInfo = info.getAdvancedInfo();
                if (advancedInfo != null)
                    updateFiles(advancedInfo.filesReceivedBytes, advancedInfo.filesAvailability);
            }
        }
    };

    private void checkParamsChanged(int propertyId) {
        Torrent torrent = info.getTorrent();
        TorrentInfo ti = info.getTorrentInfo();
        if (torrent == null || ti == null)
            return;

        switch (propertyId) {
            case BR.name:
                String name = mutableParams.getName();
                if (name != null && !torrent.name.equals(name))
                    engine.setTorrentName(torrentId, name);

                break;
            case BR.dirPath:
                Uri dirPath = mutableParams.getDirPath();
                if (dirPath != null && !torrent.downloadPath.equals(dirPath))
                    engine.setDownloadPath(torrentId, dirPath);

                break;
            case BR.sequentialDownload:
                boolean sequential = mutableParams.isSequentialDownload();
                if (ti.sequentialDownload != sequential)
                    engine.setSequentialDownload(torrentId, sequential);

                break;
            case BR.prioritiesChanged:
                if (mutableParams.isPrioritiesChanged()) {
                    Priority[] priorities = getFilePriorities();
                    if (priorities != null) {
                        if (!checkFreeSpace())
                            freeSpaceError.onNext(true);
                        engine.prioritizeFiles(torrentId, priorities);
                    }
                }
            case BR.firstLastPiecePriority:
                var firstLastPiecePriority = mutableParams.isFirstLastPiecePriority();
                if (ti.firstLastPiecePriority != firstLastPiecePriority) {
                    engine.setFirstLastPiecePriority(torrentId, firstLastPiecePriority);
                }

                break;
        }
    }

    private void startMakeFileTree() {
        disposable.add(Completable.fromRunnable(this::makeFileTree)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> updateCurDir(fileTree)));
    }

    private void makeFileTree() {
        try {
            syncBuildFileTree.lock();

            if (fileTree != null)
                return;

            TorrentMetaInfo metaInfo = info.getMetaInfo();
            if (metaInfo == null)
                return;
            List<BencodeFileItem> files = metaInfo.fileList;
            if (files.isEmpty())
                return;
            Torrent torrent = info.getTorrent();
            TorrentInfo ti = info.getTorrentInfo();
            if (torrent == null || ti == null || ti.filePriorities.length != metaInfo.fileCount)
                return;

            ArrayList<FilePriority> priorities = new ArrayList<>();
            for (Priority p : ti.filePriorities)
                priorities.add(new FilePriority(p));

            Pair<TorrentContentFileTree, TorrentContentFileTree[]> res = TorrentContentFileTreeUtils.buildFileTree(files);
            TorrentContentFileTree fileTree = res.first;
            treeLeaves = res.second;

            /* Set priorities */
            for (int i = 0; i < files.size(); i++) {
                BencodeFileItem f = files.get(i);
                TorrentContentFileTree file = treeLeaves[f.getIndex()];
                if (file != null)
                    file.setPriority(priorities.get(i), false);
            }

            this.fileTree = fileTree;

        } finally {
            syncBuildFileTree.unlock();
        }
    }

    private void updateFiles(long[] receivedBytes, double[] availability) {
        disposable.add(Completable.fromRunnable(() -> {
                    try {
                        syncBuildFileTree.lock();

                        if (fileTree == null)
                            return;

                        if (receivedBytes != null) {
                            for (int i = 0; i < receivedBytes.length; i++) {
                                // TODO
                                TorrentContentFileTree file = treeLeaves[i];
                                if (file != null)
                                    file.setReceivedBytes(receivedBytes[i]);
                            }
                        }
                        if (availability != null) {
                            for (int i = 0; i < availability.length; i++) {
                                TorrentContentFileTree file = treeLeaves[i];
                                if (file != null)
                                    file.setAvailability(availability[i]);
                            }
                        }

                    } finally {
                        syncBuildFileTree.unlock();
                    }
                })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::updateChildren));
    }

    private void updateCurDir(TorrentContentFileTree node) {
        curDir = node;
        updateChildren();
    }

    private void updateChildren() {
        children.onNext(getChildren(curDir));
    }
}
