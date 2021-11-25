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

package org.proninyaroslav.libretorrent.ui.createtorrent;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.databinding.Observable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.TorrentBuilder;
import org.proninyaroslav.libretorrent.core.exception.NormalizeUrlException;
import org.proninyaroslav.libretorrent.core.exception.UnknownUriException;
import org.proninyaroslav.libretorrent.core.model.TorrentEngine;
import org.proninyaroslav.libretorrent.core.system.FileSystemFacade;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;
import org.proninyaroslav.libretorrent.core.urlnormalizer.NormalizeUrl;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import io.reactivex.Completable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class CreateTorrentViewModel extends AndroidViewModel
{
    private static final String TAG = CreateTorrentViewModel.class.getSimpleName();

    public CreateTorrentMutableParams mutableParams = new CreateTorrentMutableParams();
    private MutableLiveData<BuildState> state = new MutableLiveData<>();
    private MutableLiveData<Integer> buildProgress = new MutableLiveData<>();
    public Throwable errorReport;
    private TorrentEngine engine;
    private FileSystemFacade fs;
    private CompositeDisposable disposables = new CompositeDisposable();

    public static class BuildState
    {
        public enum Status { UNKNOWN, BUILDING, FINISHED, ERROR }

        public Status status;
        public Throwable err;

        public BuildState(Status status, Throwable err)
        {
            this.status = status;
            this.err = err;
        }
    }

    public static class InvalidTrackerException extends Exception
    {
        public String url;

        public InvalidTrackerException(@NonNull String url)
        {
            this.url = url;
        }
    }

    public static class InvalidWebSeedException extends Exception
    {
        public String url;

        public InvalidWebSeedException(@NonNull String url)
        {
            this.url = url;
        }
    }

    public CreateTorrentViewModel(@NonNull Application application)
    {
        super(application);

        engine = TorrentEngine.getInstance(application);
        disposables.add(engine.observeNeedStartEngine()
                .subscribeOn(Schedulers.io())
                .filter((needStart) -> needStart)
                .subscribe((needStart) -> engine.start()));

        fs = SystemFacadeHelper.getFileSystemFacade(application);
        mutableParams.getSeedPath().addOnPropertyChangedCallback(dirPathCallback);
        state.setValue(new BuildState(BuildState.Status.UNKNOWN, null));
        buildProgress.setValue(0);
    }

    @Override
    protected void onCleared()
    {
        super.onCleared();

        mutableParams.getSeedPath().removeOnPropertyChangedCallback(dirPathCallback);
        disposables.clear();
    }

    public LiveData<BuildState> getState()
    {
        return state;
    }

    public LiveData<Integer> getBuildProgress()
    {
        return buildProgress;
    }

    public void setPiecesSizeIndex(int index)
    {
        if (index < 0 || index >= engine.getPieceSizeList().length)
            return;

        mutableParams.setPieceSizeIndex(index);
    }

    public void buildTorrent()
    {
        state.setValue(new BuildState(BuildState.Status.BUILDING, null));

        TorrentBuilder builder;
        try {
            builder = makeBuilder();

        } catch (Exception e) {
            onBuildError(e);

            return;
        }

        resetPercentProgress();
        disposables.add(builder.observeProgress()
                .subscribeOn(Schedulers.io())
                .filter(Objects::nonNull)
                .subscribe(this::makePercentProgress)
        );
        disposables.add(builder.build()
                .subscribeOn(Schedulers.io())
                .subscribe(this::onBuildSuccess, this::onBuildError)
        );
    }

    private TorrentBuilder makeBuilder() throws Exception
    {
        Uri seedPath = mutableParams.getSeedPath().get();
        if (seedPath == null)
            throw new IllegalArgumentException("Seed path is null");
        Uri savePath = mutableParams.getSavePath();
        if (savePath == null)
            throw new IllegalArgumentException("Save path is null");

        if (!Utils.isFileSystemPath(seedPath))
            throw new IllegalArgumentException("SAF doesn't supported");

        return new TorrentBuilder(getApplication())
                .setSeedPath(seedPath)
                .setPieceSize(getPieceSizeByIndex(mutableParams.getPieceSizeIndex()))
                .addTrackers(getAndValidateTrackers())
                .addUrlSeeds(getAndValidateWebSeeds())
                .setAsPrivate(mutableParams.isPrivateTorrent())
                .setCreator(makeCreator())
                .setComment(mutableParams.getComments())
                .setFileNameFilter((fileName) -> {
                    List<String> skipFilesList = decodeSkipFilesList();
                    if (skipFilesList.isEmpty())
                        return true;

                    for (String skipFile : skipFilesList)
                        if (fileName.toLowerCase().endsWith(skipFile.toLowerCase().trim()))
                            return false;

                    return true;
                });
    }

    private void onBuildSuccess(byte[] bencode)
    {
        Uri savePath = mutableParams.getSavePath();
        if (savePath != null) {
            try {
                fs.write(bencode, savePath);

            } catch (IOException | UnknownUriException e) {
                onBuildError(e);

                return;
            }
        }

        state.postValue(new BuildState(BuildState.Status.FINISHED, null));
    }

    private void onBuildError(Throwable e)
    {
        Uri savePath = mutableParams.getSavePath();
        if (savePath != null) {
            try {
                fs.deleteFile(savePath);
            } catch (IOException | UnknownUriException eio) {
                /* Ignore */
            }
        }

        state.postValue(new BuildState(BuildState.Status.ERROR, e));
    }

    private void makePercentProgress(TorrentBuilder.Progress progress)
    {
        buildProgress.postValue((int)(progress.piece * 100.0) / progress.numPieces);
    }

    private void resetPercentProgress()
    {
        buildProgress.postValue(0);
    }

    private String makeCreator()
    {
        Context context = getApplication();
        String creator = context.getString(R.string.app_name);
        String versionName = SystemFacadeHelper.getSystemFacade(context)
                .getAppVersionName();
        if (versionName == null)
            return creator;

        return creator + " " + versionName;
    }

    private List<TorrentBuilder.Tracker> getAndValidateTrackers() throws InvalidTrackerException
    {
        List<TorrentBuilder.Tracker> validTrackers = new ArrayList<>();
        int tier = 0;
        for (String url : decodeUrls(mutableParams.getTrackerUrls())) {
            try {
                url = normalizeAndValidateUrl(url);

            } catch (IllegalArgumentException e) {
                throw new InvalidTrackerException(url);
            }
            validTrackers.add(new TorrentBuilder.Tracker(url, tier++));
        }

        return validTrackers;
    }

    private List<String> getAndValidateWebSeeds() throws InvalidWebSeedException
    {
        List<String> validWebSeeds = new ArrayList<>();
        for (String url : decodeUrls(mutableParams.getWebSeedUrls())) {
            try {
                url = normalizeAndValidateUrl(url);

            } catch (IllegalArgumentException e) {
                throw new InvalidWebSeedException(url);
            }
            validWebSeeds.add(url);
        }

        return validWebSeeds;
    }

    private String[] decodeUrls(String urlsStr)
    {
        String[] urls = new String[0];
        if (!TextUtils.isEmpty(urlsStr))
            urls = urlsStr.split("\n");

        return urls;
    }

    private String normalizeAndValidateUrl(String url)
    {
        NormalizeUrl.Options options = new NormalizeUrl.Options();
        options.decode = false;
        try {
            url = NormalizeUrl.normalize(url, options);

        } catch (NormalizeUrlException e) {
            throw new IllegalArgumentException();
        }

        if (!Utils.isValidTrackerUrl(url))
            throw new IllegalArgumentException();

        return url;
    }

    private List<String> decodeSkipFilesList()
    {
        List<String> skipFilesList = new ArrayList<>();
        if (!TextUtils.isEmpty(mutableParams.getSkipFiles()))
            skipFilesList = new ArrayList<>(Arrays.asList(mutableParams.getSkipFiles()
                    .split(CreateTorrentMutableParams.FILTER_SEPARATOR)));

        return skipFilesList;
    }

    private int getPieceSizeByIndex(int index)
    {
        return engine.getPieceSizeList()[index] * 1024;
    }

    public Completable downloadTorrent() throws UnknownUriException {
        /* Use seed path parent; otherwise use save torrent file path */
        Uri savePath;
        Uri seedPath = mutableParams.getSeedPath().get();
        if (seedPath != null) {
            savePath = fs.getParentDirUri(seedPath);
            if (savePath == null)
                savePath = mutableParams.getSavePath();
        } else {
            savePath = mutableParams.getSavePath();
        }
        Uri torrentFilePath = mutableParams.getSavePath();
        if (savePath == null || torrentFilePath == null)
            return Completable.complete();

        Uri path = savePath;
        return Completable.create((emitter) -> {
            if (emitter.isDisposed())
                return;

            Disposable d = engine.observeEngineRunning()
                    .subscribeOn(Schedulers.io())
                    .subscribe((isRunning) -> {
                        if (isRunning) {
                            engine.addTorrent(torrentFilePath, path);
                            if (!emitter.isDisposed())
                                emitter.onComplete();
                        }
                    });

            emitter.setDisposable(d);
        });
    }

    public void finish()
    {
        disposables.clear();
    }

    private Observable.OnPropertyChangedCallback dirPathCallback = new Observable.OnPropertyChangedCallback()
    {
        @Override
        public void onPropertyChanged(Observable sender, int propertyId)
        {
            Uri seedPath = mutableParams.getSeedPath().get();
            if (seedPath == null)
                return;
            try {
                mutableParams.setSeedPathName(fs.getDirPath(seedPath));
            } catch (UnknownUriException e) {
                Log.e(TAG, Log.getStackTraceString(e));
            }
        }
    };
}
