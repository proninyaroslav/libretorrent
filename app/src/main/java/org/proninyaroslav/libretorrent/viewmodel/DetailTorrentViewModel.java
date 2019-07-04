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

package org.proninyaroslav.libretorrent.viewmodel;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.databinding.Observable;
import androidx.databinding.library.baseAdapters.BR;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import org.libtorrent4j.Priority;
import org.proninyaroslav.libretorrent.MainApplication;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.BencodeFileItem;
import org.proninyaroslav.libretorrent.core.ChangeableParams;
import org.proninyaroslav.libretorrent.core.TorrentEngine;
import org.proninyaroslav.libretorrent.core.TorrentMetaInfo;
import org.proninyaroslav.libretorrent.core.TorrentStateProvider;
import org.proninyaroslav.libretorrent.core.entity.Torrent;
import org.proninyaroslav.libretorrent.core.exceptions.FreeSpaceException;
import org.proninyaroslav.libretorrent.core.filetree.FileNode;
import org.proninyaroslav.libretorrent.core.filetree.FilePriority;
import org.proninyaroslav.libretorrent.core.filetree.TorrentContentFileTree;
import org.proninyaroslav.libretorrent.core.server.TorrentStreamServer;
import org.proninyaroslav.libretorrent.core.stateparcel.AdvanceStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.BasicStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.PeerStateParcel;
import org.proninyaroslav.libretorrent.core.stateparcel.TrackerStateParcel;
import org.proninyaroslav.libretorrent.core.storage.TorrentRepository;
import org.proninyaroslav.libretorrent.core.utils.FileTreeDepthFirstSearch;
import org.proninyaroslav.libretorrent.core.utils.FileUtils;
import org.proninyaroslav.libretorrent.core.utils.TorrentContentFileTreeUtils;
import org.proninyaroslav.libretorrent.settings.SettingsManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;

public class DetailTorrentViewModel extends AndroidViewModel
{
    private String torrentId;
    private TorrentStateProvider stateProvider;
    private TorrentEngine engine;
    private TorrentRepository repo;
    private SharedPreferences pref;
    private CompositeDisposable disposable = new CompositeDisposable();
    public TorrentDetailsInfo info = new TorrentDetailsInfo();
    public TorrentDetailsMutableParams mutableParams = new TorrentDetailsMutableParams();
    public MutableLiveData<Boolean> paramsChanged = new MutableLiveData<>();
    public Throwable errorReport;

    public TorrentContentFileTree fileTree;
    public BehaviorSubject<List<TorrentContentFileTree>> children = BehaviorSubject.create();
    /* Current directory */
    private TorrentContentFileTree curDir;

    public DetailTorrentViewModel(@NonNull Application application)
    {
        super(application);

        stateProvider = ((MainApplication)getApplication()).getTorrentStateProvider();
        engine = TorrentEngine.getInstance(application);
        repo = ((MainApplication)getApplication()).getTorrentRepository();
        pref = SettingsManager.getInstance(application).getPreferences();
        paramsChanged.setValue(false);
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
        paramsChanged.setValue(false);
        fileTree = null;
    }

    public Flowable<BasicStateParcel> observeTorrentState()
    {
        return stateProvider.observeState(torrentId);
    }

    public Flowable<AdvanceStateParcel> observeAdvancedTorrentState()
    {
        return stateProvider.observeAdvancedState(torrentId);
    }

    public Flowable<Torrent> observeTorrent()
    {
        return repo.observeTorrentById(torrentId);
    }

    public Flowable<List<TrackerStateParcel>> observeTrackers()
    {
        return stateProvider.observeTrackersState(torrentId);
    }

    public Flowable<List<PeerStateParcel>> observePeers()
    {
        return stateProvider.observePeersState(torrentId);
    }

    public Flowable<boolean[]> observePieces()
    {
        return stateProvider.observePiecesState(torrentId);
    }

    public Flowable<Pair<Torrent, BasicStateParcel>> observeTorrentInfo()
    {
        return Flowable.combineLatest(observeTorrent(), observeTorrentState(), Pair::create);
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

    public void updateInfo(Torrent torrent, BasicStateParcel state)
    {
        boolean firstUpdate = info.getTorrent() == null;

        info.setTorrent(torrent);
        info.setState(state);
        info.setLeechers(calcLeechers());
        info.setTotalLeechers(calcTotalLeechers());

        if (firstUpdate)
            initMutableParams();
        if (fileTree == null)
            startMakeFileTree();
    }

    public void updateInfo(AdvanceStateParcel advancedState)
    {
        info.setAdvancedState(advancedState);
    }

    public int calcLeechers()
    {
        BasicStateParcel state = info.getState();
        AdvanceStateParcel advancedState = info.getAdvancedState();
        if (state == null || advancedState == null)
            return 0;

        return Math.abs(state.peers - advancedState.seeds);
    }

    public int calcTotalLeechers()
    {
        BasicStateParcel state = info.getState();
        AdvanceStateParcel advancedState = info.getAdvancedState();
        if (state == null || advancedState == null)
            return 0;

        return state.totalPeers - advancedState.totalSeeds;
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

        mutableParams.setPrioritiesChanged(true);
        node.select((selected ? TorrentContentFileTree.SelectState.SELECTED :
                TorrentContentFileTree.SelectState.UNSELECTED));
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

        Uri path = FileUtils.getFileUri(getApplication(), relativePath, torrent.downloadPath);
        if (path == null)
            return null;

        if (FileUtils.isFileSystemPath(path))
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
                .doAfterTerminate(() -> {
                    if (!fileNames.isEmpty())
                        mutableParams.setPrioritiesChanged(true);
                })
                .subscribe((file) -> file.setPriority(priority)));
    }

    public String getStreamUrl(int fileIndex)
    {
        String hostname = pref.getString(getApplication().getString(R.string.pref_key_streaming_hostname),
                                         SettingsManager.Default.streamingHostname);
        int port = pref.getInt(getApplication().getString(R.string.pref_key_streaming_port),
                               SettingsManager.Default.streamingPort);

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

    public void applyChangedParams() throws FreeSpaceException
    {
        if (!checkFreeSpace())
            throw new FreeSpaceException();

        Torrent torrent = info.getTorrent();
        if (torrent == null)
            return;

        paramsChanged.setValue(false);
        disableSelectedFiles();
        engine.changeParams(torrentId, makeParams(torrent));
    }

    public String makeMagnet(boolean includePriorities)
    {
        return engine.makeMagnet(torrentId, includePriorities);
    }

    public void copyTorrentFile(@NonNull Uri destFile) throws IOException
    {
        String srcFile = repo.getTorrentFile(getApplication(), torrentId);
        if (srcFile == null)
            throw new FileNotFoundException();

        FileUtils.copyFile(getApplication(), Uri.fromFile(new File(srcFile)), destFile);
    }

    public void setSpeedLimit(int uploadSpeedLimit, int downloadSpeedLimit)
    {
        engine.setUploadSpeedLimit(torrentId, uploadSpeedLimit);
        engine.setDownloadSpeedLimit(torrentId, downloadSpeedLimit);
    }

    private ChangeableParams makeParams(Torrent torrent)
    {
        ChangeableParams params = new ChangeableParams();

        String name = mutableParams.getName();
        Uri dirPath = mutableParams.getDirPath();
        boolean sequential = mutableParams.isSequentialDownload();
        boolean prioritiesChanged = mutableParams.isPrioritiesChanged();

        if (!torrent.name.equals(name))
            params.name = name;
        if (!torrent.downloadPath.equals(dirPath))
            params.dirPath = dirPath;
        if (torrent.sequentialDownload != sequential)
            params.sequentialDownload = sequential;
        if (prioritiesChanged)
            params.priorities = getFilePriorities();

        return params;
    }

    private Priority[] getFilePriorities()
    {
        List<TorrentContentFileTree> files = TorrentContentFileTreeUtils.getFiles(fileTree);
        if (files == null)
            return null;

        Priority[] priorities = new Priority[files.size()];
        for (TorrentContentFileTree file : files)
            if (file != null && (file.getIndex() >= 0 && file.getIndex() < files.size()))
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
        mutableParams.setSequentialDownload(torrent.sequentialDownload);
    }

    private final Observable.OnPropertyChangedCallback mutableParamsCallback = new Observable.OnPropertyChangedCallback()
    {
        @Override
        public void onPropertyChanged(Observable sender, int propertyId)
        {
            if (propertyId == BR.dirPath) {
                Uri dirPath = mutableParams.getDirPath();
                if (dirPath != null) {
                    info.setStorageFreeSpace(FileUtils.getDirAvailableBytes(getApplication(), dirPath));
                    info.setDirName(FileUtils.getDirName(getApplication(), dirPath));
                }
            }

            checkParamsChanged();
        }
    };

    private final Observable.OnPropertyChangedCallback infoCallback = new Observable.OnPropertyChangedCallback()
    {
        @Override
        public void onPropertyChanged(Observable sender, int propertyId)
        {
            if (propertyId == BR.advancedState) {
                AdvanceStateParcel advancedState = info.getAdvancedState();
                if (advancedState != null)
                    updateFiles(advancedState.filesReceivedBytes, advancedState.filesAvailability);
            }
        }
    };

    private void checkParamsChanged()
    {
        Torrent torrent = info.getTorrent();
        if (torrent == null)
            return;

        boolean changed = !torrent.name.equals(mutableParams.getName()) ||
                !torrent.downloadPath.equals(mutableParams.getDirPath()) ||
                torrent.sequentialDownload != mutableParams.isSequentialDownload() ||
                mutableParams.isPrioritiesChanged();

        paramsChanged.setValue(changed);
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
        if (fileTree != null)
            return;

        TorrentMetaInfo metaInfo = info.getMetaInfo();
        if (metaInfo == null)
            return;
        List<BencodeFileItem> files = metaInfo.fileList;
        if (files.isEmpty())
            return;
        Torrent torrent = info.getTorrent();
        if (torrent == null || torrent.filePriorities.size() != metaInfo.fileCount)
            return;

        ArrayList<FilePriority> priorities = new ArrayList<>();
        for (Priority p : torrent.filePriorities)
            priorities.add(new FilePriority(p));

        TorrentContentFileTree fileTree = TorrentContentFileTreeUtils.buildFileTree(files);
        FileTreeDepthFirstSearch<TorrentContentFileTree> search = new FileTreeDepthFirstSearch<>();
        /* Set priority for selected files */
        for (int i = 0; i < files.size(); i++) {
            if (priorities.get(i).getType() != FilePriority.Type.IGNORE) {
                BencodeFileItem f = files.get(i);

                TorrentContentFileTree file = search.find(fileTree, f.getIndex());
                if (file != null) {
                    file.setPriority(priorities.get(i));
                    /*
                     * Disable the ability to select the file
                     * because it's being downloaded/download
                     */
                    file.select(TorrentContentFileTree.SelectState.DISABLED);
                }
            }
        }

        this.fileTree = fileTree;
    }

    private void updateFiles(long[] receivedBytes, double[] availability)
    {
        disposable.add(Completable.fromRunnable(() -> {
            if (fileTree == null)
                return;

            Map<Integer, TorrentContentFileTree> files = TorrentContentFileTreeUtils.getFilesAsMap(fileTree);
            if (receivedBytes != null) {
                for (int i = 0; i < receivedBytes.length; i++) {
                    TorrentContentFileTree file = files.get(i);
                    if (file != null)
                        file.setReceivedBytes(receivedBytes[i]);
                }
            }
            if (availability != null) {
                for (int i = 0; i < availability.length; i++) {
                    TorrentContentFileTree file = files.get(i);
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
        List<TorrentContentFileTree> files = TorrentContentFileTreeUtils.getFiles(fileTree);
        if (files == null)
            return;

        for (TorrentContentFileTree file : files)
            if (file != null && file.getSelectState() == TorrentContentFileTree.SelectState.SELECTED)
                file.select(TorrentContentFileTree.SelectState.DISABLED);
    }
}
