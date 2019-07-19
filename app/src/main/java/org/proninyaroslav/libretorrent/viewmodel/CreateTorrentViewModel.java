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
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.databinding.Observable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.libtorrent4j.Pair;
import org.libtorrent4j.TorrentBuilder;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.TorrentEngine;
import org.proninyaroslav.libretorrent.core.TorrentSession;
import org.proninyaroslav.libretorrent.core.exceptions.NormalizeUrlException;
import org.proninyaroslav.libretorrent.core.utils.FileUtils;
import org.proninyaroslav.libretorrent.core.utils.NormalizeUrl;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CreateTorrentViewModel extends AndroidViewModel
{
    public CreateTorrentMutableParams mutableParams = new CreateTorrentMutableParams();
    private MutableLiveData<BuildState> state = new MutableLiveData<>();
    private MutableLiveData<Integer> buildProgress = new MutableLiveData<>();
    private BuildTorrentTask buildTask;
    public Throwable errorReport;
    private TorrentEngine engine;

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
    };

    public static class InvalidWebSeedException extends Exception
    {
        public String url;

        public InvalidWebSeedException(@NonNull String url)
        {
            this.url = url;
        }
    };

    public CreateTorrentViewModel(@NonNull Application application)
    {
        super(application);

        engine = TorrentEngine.getInstance(application);
        mutableParams.getSeedPath().addOnPropertyChangedCallback(dirPathCallback);
        state.setValue(new BuildState(BuildState.Status.UNKNOWN, null));
        buildProgress.setValue(0);
    }

    @Override
    protected void onCleared()
    {
        super.onCleared();

        mutableParams.getSeedPath().removeOnPropertyChangedCallback(dirPathCallback);
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
        if (index < 0 || index >= TorrentSession.pieceSize.length)
            return;

        mutableParams.setPieceSizeIndex(index);
    }

    public void buildTorrent()
    {
        /*
         * The AsyncTask class must be loaded on the UI thread. This is done automatically as of JELLY_BEAN.
         * http://developer.android.com/intl/ru/reference/android/os/AsyncTask.html
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            buildTask = new BuildTorrentTask(this);
            buildTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mutableParams);
        } else {
            Handler handler = new Handler(getApplication().getMainLooper());
            handler.post(() -> {
                buildTask = new BuildTorrentTask(this);
                buildTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mutableParams);
            });
        }
    }

    public void downloadTorrent()
    {
        Uri savePath = mutableParams.getSavePath();
        if (savePath == null)
            return;

        engine.addTorrent(savePath);
    }

    public void finish()
    {
        if (buildTask != null)
            buildTask.cancel(true);
    }

    private Observable.OnPropertyChangedCallback dirPathCallback = new Observable.OnPropertyChangedCallback()
    {
        @Override
        public void onPropertyChanged(Observable sender, int propertyId)
        {
            Uri seedPath = mutableParams.getSeedPath().get();
            if (seedPath == null)
                return;

            mutableParams.setSeedPathName(FileUtils.getDirName(getApplication(), seedPath));
        }
    };

    private static class BuildTorrentTask extends AsyncTask<CreateTorrentMutableParams, Void, Throwable>
    {
        private WeakReference<CreateTorrentViewModel> viewModel;
        private CreateTorrentMutableParams mutableParams;

        private BuildTorrentTask(CreateTorrentViewModel viewModel)
        {
            this.viewModel = new WeakReference<>(viewModel);
        }

        @Override
        protected void onPreExecute()
        {
            CreateTorrentViewModel v = viewModel.get();
            if (v == null)
                return;

            v.state.setValue(new BuildState(BuildState.Status.BUILDING, null));
        }

        @Override
        protected Exception doInBackground(CreateTorrentMutableParams... params)
        {
            CreateTorrentViewModel v = viewModel.get();
            if (v == null || isCancelled())
                return null;

            mutableParams = params[0];

            Exception err = null;
            try {
                Uri seedPath = mutableParams.getSeedPath().get();
                if (seedPath == null)
                    throw new IllegalArgumentException("Seed path is null");
                Uri savePath = mutableParams.getSavePath();
                if (savePath == null)
                    throw new IllegalArgumentException("Save path is null");

                /* TODO: SAF support */
                if (!FileUtils.isFileSystemPath(seedPath))
                    throw new IllegalArgumentException("SAF doesn't supported");

                TorrentBuilder builder = new TorrentBuilder()
                        .path(new File(seedPath.getPath()))
                        .pieceSize(getPieceSizeByIndex(mutableParams.getPieceSizeIndex()))
                        .addTrackers(getAndValidateTrackers())
                        .addUrlSeeds(getAndValidateWebSeeds())
                        .setPrivate(mutableParams.isPrivateTorrent())
                        .creator(makeCreator())
                        .comment(mutableParams.getComments())
                        .listener(new TorrentBuilder.Listener() {
                            @Override
                            public boolean accept(String filename)
                            {
                                if (isCancelled())
                                    return false;

                                List<String> skipFilesList = decodeSkipFilesList();
                                if (skipFilesList.isEmpty())
                                    return true;

                                for (String skipFile : skipFilesList)
                                    if (filename.toLowerCase().endsWith(skipFile.toLowerCase().trim()))
                                        return false;

                                return true;
                            }

                            @Override
                            public void progress(int pieceIndex, int numPieces)
                            {
                                CreateTorrentViewModel v = viewModel.get();
                                if (v == null || isCancelled())
                                    return;

                                v.buildProgress.postValue((int)(pieceIndex * 100.0) / numPieces);
                            }
                        });
                if (mutableParams.isOptimizeAlignment())
                    builder.flags(builder.flags().or_(TorrentBuilder.OPTIMIZE_ALIGNMENT));
                else
                    builder.flags(builder.flags().and_(TorrentBuilder.OPTIMIZE_ALIGNMENT.inv()));

                 byte[] bencode = builder.generate().entry().bencode();
                 FileUtils.write(v.getApplication(), bencode, savePath);

            } catch (Exception e) {
                err = e;
                Uri savePath = mutableParams.getSavePath();
                if (savePath != null) {
                    try {
                        FileUtils.deleteFile(v.getApplication(), savePath);

                    } catch (IOException ioe) {
                        /* Ignore */
                    }
                }
            }

            return err;
        }

        private String makeCreator()
        {
            CreateTorrentViewModel v = viewModel.get();
            if (v == null)
                return "";

            Context context = v.getApplication();
            String creator = context.getString(R.string.app_name);
            String versionName = Utils.getAppVersionName(context);
            if (versionName == null)
                return creator;

            return creator + " " + versionName;
        }

        private List<Pair<String, Integer>> getAndValidateTrackers() throws InvalidTrackerException
        {
            List<Pair<String, Integer>> validTrackers = new ArrayList<>();
            int tier = 0;
            for (String url : decodeUrls(mutableParams.getTrackerUrls())) {
                try {
                    url = normalizeAndValidateUrl(url);

                } catch (IllegalArgumentException e) {
                    throw new InvalidTrackerException(url);
                }
                validTrackers.add(new Pair<>(url, tier++));
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
            return TorrentSession.pieceSize[index] * 1024;
        }

        @Override
        protected void onPostExecute(Throwable err) {
            CreateTorrentViewModel v = viewModel.get();
            if (v == null)
                return;

            if (err == null)
                v.state.setValue(new BuildState(BuildState.Status.FINISHED, null));
            else
                v.state.setValue(new BuildState(BuildState.Status.ERROR, err));
        }
    }
}
