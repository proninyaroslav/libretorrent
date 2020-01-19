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

package org.proninyaroslav.libretorrent.ui.detailtorrent;

import android.app.Application;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.core.util.Pair;
import androidx.databinding.Observable;
import androidx.databinding.library.baseAdapters.BR;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.model.TorrentEngine;
import org.proninyaroslav.libretorrent.core.model.TorrentInfoProvider;
import org.proninyaroslav.libretorrent.core.model.data.AdvancedTorrentInfo;
import org.proninyaroslav.libretorrent.core.model.data.PeerInfo;
import org.proninyaroslav.libretorrent.core.model.data.Priority;
import org.proninyaroslav.libretorrent.core.model.data.TorrentInfo;
import org.proninyaroslav.libretorrent.core.model.data.TrackerInfo;
import org.proninyaroslav.libretorrent.core.model.data.entity.Torrent;
import org.proninyaroslav.libretorrent.core.model.data.metainfo.BencodeFileItem;
import org.proninyaroslav.libretorrent.core.model.data.metainfo.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.core.model.filetree.FileNode;
import org.proninyaroslav.libretorrent.core.model.filetree.FilePriority;
import org.proninyaroslav.libretorrent.core.model.filetree.TorrentContentFileTree;
import org.proninyaroslav.libretorrent.core.model.stream.TorrentStreamServer;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.core.storage.TorrentRepository;
import org.proninyaroslav.libretorrent.core.system.FileSystemFacade;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;
import org.proninyaroslav.libretorrent.core.utils.TorrentContentFileTreeUtils;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;

public class DetailTorrentViewModel extends AndroidViewModel
{
    private String torrentId;
    private TorrentInfoProvider infoProvider;
    private TorrentEngine engine;
    private TorrentRepository repo;
    private FileSystemFacade fs;
    private SettingsRepository pref;
    private CompositeDisposable disposable = new CompositeDisposable();
    public TorrentDetailsInfo info = new TorrentDetailsInfo();
    public TorrentDetailsMutableParams mutableParams = new TorrentDetailsMutableParams();
    private PublishSubject<Boolean> freeSpaceError = PublishSubject.create();
    public Throwable errorReport;

    private ReentrantLock syncBuildFileTree = new ReentrantLock();
    public TorrentContentFileTree fileTree;
    private TorrentContentFileTree[] treeLeaves;
    private BehaviorSubject<List<TorrentContentFileTree>> children = BehaviorSubject.create();
    /* Current directory */
    private TorrentContentFileTree curDir;

    public DetailTorrentViewModel(@NonNull Application application)
    {
        super(application);

        infoProvider = TorrentInfoProvider.getInstance(application);
        engine = TorrentEngine.getInstance(application);
        repo = RepositoryHelper.getTorrentRepository(application);
        fs = SystemFacadeHelper.getFileSystemFacade(application);
        pref = RepositoryHelper.getSettingsRepository(application);
        mutableParams.addOnPropertyChangedCallback(mutableParamsCallback);
        info.addOnPropertyChangedCallback(infoCallback);
    }

    @Override
    protected void onCleared()
    {
        super.onCleared();

        disposable.clear();
        mutableParams.removeOnPropertyChangedCallback(mutableParamsCallback);
        info.removeOnPropertyChangedCallback(infoCallback);
    }

    public void setTorrentId(@NonNull String id)
    {
        torrentId = id;
    }

    public void clearData()
    {
        disposable.clear();
        torrentId = null;
        info.removeOnPropertyChangedCallback(infoCallback);
        info = new TorrentDetailsInfo();
        info.addOnPropertyChangedCallback(infoCallback);
        mutableParams.removeOnPropertyChangedCallback(mutableParamsCallback);
        mutableParams = new TorrentDetailsMutableParams();
        mutableParams.addOnPropertyChangedCallback(mutableParamsCallback);
        fileTree = null;
        treeLeaves = null;
    }

    public io.reactivex.Observable<Boolean> observeFreeSpaceError()
    {
        return freeSpaceError;
    }

    public io.reactivex.Observable<List<TorrentContentFileTree>> getDirChildren()
    {
        return children;
    }

    public Flowable<TorrentInfo> observeTorrentInfo()
    {
        return infoProvider.observeInfo(torrentId);
    }

    public Flowable<AdvancedTorrentInfo> observeAdvancedTorrentInfo()
    {
        return infoProvider.observeAdvancedInfo(torrentId);
    }

    public Flowable<Torrent> observeTorrent()
    {
        return repo.observeTorrentById(torrentId);
    }

    public Flowable<List<TrackerInfo>> observeTrackers()
    {
        return infoProvider.observeTrackersInfo(torrentId);
    }

    public Flowable<List<PeerInfo>> observePeers()
    {
        return infoProvider.observePeersInfo(torrentId);
    }

    public Flowable<boolean[]> observePieces()
    {
        return infoProvider.observePiecesInfo(torrentId);
    }

    public Flowable<Pair<Torrent, TorrentInfo>> observeTorrentInfoPair()
    {
        return Flowable.combineLatest(observeTorrent(), observeTorrentInfo(), Pair::create);
    }

    public Flowable<TorrentMetaInfo> observeTorrentMetaInfo()
    {
        return engine.observeTorrentMetaInfo(torrentId);
    }

    public void updateInfo(TorrentMetaInfo metaInfo)
    {
        info.setMetaInfo(metaInfo);

        if (fileTree == null)
            startMakeFileTree();
    }

    public void updateInfo(Torrent torrent, TorrentInfo ti)
    {
        boolean firstUpdate = info.getTorrent() == null;

        info.setTorrent(torrent);
        info.setTorrentInfo(ti);

        if (firstUpdate)
            initMutableParams();
        if (fileTree == null)
            startMakeFileTree();
    }

    public void updateInfo(AdvancedTorrentInfo advancedInfo)
    {
        info.setAdvancedInfo(advancedInfo);
    }

    /*
     * Navigate back to an upper directory.
     */

    public void upToParentDirectory()
    {
        updateCurDir(curDir.getParent());
    }

    public List<TorrentContentFileTree> getChildren(TorrentContentFileTree node)
    {
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

    public void chooseDirectory(@NonNull String name)
    {
        TorrentContentFileTree node = curDir.getChild(name);
        if (node == null)
            return;

        if (node.isFile())
            node = fileTree;

        updateCurDir(node);
    }

    public boolean isFile(@NonNull String name)
    {
        TorrentContentFileTree node = curDir.getChild(name);

        return node != null && node.isFile();
    }

    public void selectFile(@NonNull String name, boolean selected)
    {
        TorrentContentFileTree node = curDir.getChild(name);
        if (node == null)
            return;

        if (node.getSelectState() == TorrentContentFileTree.SelectState.DISABLED)
            return;

        node.select((selected ?
                TorrentContentFileTree.SelectState.SELECTED :
                TorrentContentFileTree.SelectState.UNSELECTED), true);

        updateChildren();
        mutableParams.setPrioritiesChanged(true);
    }

    public Uri getFilePath(@NonNull String name)
    {
        Context context = getApplication();
        TorrentContentFileTree node = curDir.getChild(name);
        if (node == null)
            return null;

        String relativePath = node.getPath();

        Torrent torrent = info.getTorrent();
        if (torrent == null)
            return null;

        Uri path = fs.getFileUri(relativePath, torrent.downloadPath);
        if (path == null)
            return null;

        if (Utils.isFileSystemPath(path))
            path = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".provider",
                    new File(path.getPath()));

        return path;
    }

    /*
     * Compare the array with randomly selected priority.
     * If some elements not equals with this priority,
     * set priority type as MIXED, else return randomly selected priority
     */

    public FilePriority getFilesPriority(@NonNull List<String> fileNames)
    {
        List<FilePriority> priorities = new ArrayList<>();
        for (String name : fileNames) {
            TorrentContentFileTree file = curDir.getChild(name);
            if (file != null)
                priorities.add(file.getFilePriority());
        }
        if (priorities.size() == 0)
            return null;


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

        if (randomPriority != null && !isMixedPriority)
            return randomPriority;
        else
            return new FilePriority(FilePriority.Type.MIXED);
    }

    public void applyPriority(@NonNull List<String> fileNames,
                              @NonNull FilePriority priority)
    {
        disposable.add(io.reactivex.Observable.fromIterable(fileNames)
                .map((fileName) -> curDir.getChild(fileName))
                .filter((file) -> file != null)
                .subscribe((file) -> {
                    file.setPriority(priority, true);
                    updateChildren();
                    mutableParams.setPrioritiesChanged(true);
                }));
    }

    public String getStreamUrl(int fileIndex)
    {
        String hostname = pref.streamingHostname();
        int port = pref.streamingPort();

        return TorrentStreamServer.makeStreamUrl(hostname, port, torrentId, fileIndex);
    }

    public void deleteTrackers(@NonNull List<String> urls)
    {
        engine.deleteTrackers(torrentId, urls);
    }

    public void replaceTrackers(@NonNull List<String> urls)
    {
        engine.replaceTrackers(torrentId, urls);
    }

    public void addTrackers(@NonNull List<String> urls)
    {
        engine.addTrackers(torrentId, urls);
    }

    public void pauseResumeTorrent()
    {
        engine.pauseResumeTorrent(torrentId);
    }

    public void deleteTorrent(boolean withFiles)
    {
        engine.deleteTorrents(Collections.singletonList(torrentId), withFiles);
    }

    public void forceRecheckTorrent()
    {
        engine.forceRecheckTorrents(Collections.singletonList(torrentId));
    }

    public void forceAnnounceTorrent()
    {
        engine.forceAnnounceTorrents(Collections.singletonList(torrentId));
    }

    public int getUploadSpeedLimit()
    {
        return engine.getUploadSpeedLimit(torrentId);
    }

    public int getDownloadSpeedLimit()
    {
        return engine.getDownloadSpeedLimit(torrentId);
    }

    public String makeMagnet(boolean includePriorities)
    {
        return engine.makeMagnet(torrentId, includePriorities);
    }

    public void copyTorrentFile(@NonNull Uri destFile) throws IOException
    {
        byte[] bencode = engine.getBencode(torrentId);
        if (bencode == null)
            throw new IOException("Cannot read bencode");

        fs.write(bencode, destFile);
    }

    public void setSpeedLimit(int uploadSpeedLimit, int downloadSpeedLimit)
    {
        engine.setUploadSpeedLimit(torrentId, uploadSpeedLimit);
        engine.setDownloadSpeedLimit(torrentId, downloadSpeedLimit);
    }

    private Priority[] getFilePriorities()
    {
        if (treeLeaves == null)
            return null;

        Priority[] priorities = new Priority[treeLeaves.length];
        for (TorrentContentFileTree file : treeLeaves)
            if (file != null && (file.getIndex() >= 0 && file.getIndex() < treeLeaves.length))
                priorities[file.getIndex()] = file.getFilePriority().getPriority();

        return priorities;
    }

    private boolean checkFreeSpace()
    {
        long storageFreeSpace = info.getStorageFreeSpace();

        return storageFreeSpace == -1 || storageFreeSpace >= fileTree.selectedFileSize();
    }

    private void initMutableParams()
    {
        Torrent torrent = info.getTorrent();
        if (torrent == null)
            return;

        mutableParams.setName(torrent.name);
        mutableParams.setDirPath(torrent.downloadPath);
        mutableParams.setSequentialDownload(engine.isSequentialDownload(torrentId));
    }

    private final Observable.OnPropertyChangedCallback mutableParamsCallback = new Observable.OnPropertyChangedCallback()
    {
        @Override
        public void onPropertyChanged(Observable sender, int propertyId)
        {
            if (propertyId == BR.dirPath) {
                Uri dirPath = mutableParams.getDirPath();
                if (dirPath != null) {
                    info.setStorageFreeSpace(fs.getDirAvailableBytes(dirPath));
                    info.setDirName(fs.getDirPath(dirPath));
                }
            }

            checkParamsChanged(propertyId);
        }
    };

    private final Observable.OnPropertyChangedCallback infoCallback = new Observable.OnPropertyChangedCallback()
    {
        @Override
        public void onPropertyChanged(Observable sender, int propertyId)
        {
            if (propertyId == BR.advancedInfo) {
                AdvancedTorrentInfo advancedInfo = info.getAdvancedInfo();
                if (advancedInfo != null)
                    updateFiles(advancedInfo.filesReceivedBytes, advancedInfo.filesAvailability);
            }
        }
    };

    private void checkParamsChanged(int propertyId)
    {
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
                        disableSelectedFiles();
                        engine.prioritizeFiles(torrentId, priorities);
                    }
                }

                break;
        }
    }

    private void startMakeFileTree()
    {
        disposable.add(Completable.fromRunnable(this::makeFileTree)
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> updateCurDir(fileTree)));
    }

    private void makeFileTree()
    {
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

            /* Set priority for selected files */
            for (int i = 0; i < files.size(); i++) {
                BencodeFileItem f = files.get(i);
                TorrentContentFileTree file = treeLeaves[f.getIndex()];
                if (file != null) {
                    FilePriority p = priorities.get(i);
                    if (p.getType() == FilePriority.Type.IGNORE) {
                        file.setPriority(p, false);
                    } else {
                        /*
                         * Disable the ability to select the file
                         * because it's being downloaded/download
                         */
                        file.setPriorityAndDisable(p, false);
                    }
                }
            }

            this.fileTree = fileTree;

        } finally {
            syncBuildFileTree.unlock();
        }
    }

    private void updateFiles(long[] receivedBytes, double[] availability)
    {
        disposable.add(Completable.fromRunnable(() -> {
            if (fileTree == null)
                return;

            if (receivedBytes != null) {
                for (int i = 0; i < receivedBytes.length; i++) {
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
        })
        .subscribeOn(Schedulers.computation())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(this::updateChildren));
    }

    private void updateCurDir(TorrentContentFileTree node)
    {
        curDir = node;
        updateChildren();
    }

    private void updateChildren()
    {
        children.onNext(getChildren(curDir));
    }

    public void disableSelectedFiles()
    {
        if (treeLeaves == null)
            return;

        boolean changed = false;
        for (TorrentContentFileTree file : treeLeaves) {
            if (file != null && file.getSelectState() == TorrentContentFileTree.SelectState.SELECTED) {
                changed = true;
                file.select(TorrentContentFileTree.SelectState.DISABLED, true);
            }
        }

        if (changed)
            updateChildren();
    }
}
