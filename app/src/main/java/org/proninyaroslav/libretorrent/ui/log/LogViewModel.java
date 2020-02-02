/*
 * Copyright (C) 2020 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.log;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.databinding.library.baseAdapters.BR;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.logger.LogEntry;
import org.proninyaroslav.libretorrent.core.logger.Logger;
import org.proninyaroslav.libretorrent.core.model.TorrentEngine;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.service.SaveLogWorker;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LogViewModel extends AndroidViewModel
{
    private static final int PAGE_SIZE = 20;

    private TorrentEngine engine;
    private SettingsRepository pref;
    public LogMutableParams mutableParams = new LogMutableParams();
    private LogSourceFactory sourceFactory;
    private PagedList.Config pageConfig = new PagedList.Config.Builder()
            .setPageSize(PAGE_SIZE)
            .setEnablePlaceholders(false)
            .build();
    private boolean logPaused;
    private boolean recordingStopped;

    public LogViewModel(@NonNull Application application)
    {
        super(application);

        engine = TorrentEngine.getInstance(application);
        pref = RepositoryHelper.getSettingsRepository(application);

        Logger sessionLogger = engine.getSessionLogger();
        logPaused = sessionLogger.isPaused();
        recordingStopped = !sessionLogger.isRecording();
        sourceFactory = new LogSourceFactory(sessionLogger);

        initMutableParams();
    }

    private void initMutableParams()
    {
        mutableParams.setLogging(pref.logging());
        mutableParams.setLogSessionFilter(pref.logSessionFilter());
        mutableParams.setLogDhtFilter(pref.logDhtFilter());
        mutableParams.setLogPeerFilter(pref.logPeerFilter());
        mutableParams.setLogPortmapFilter(pref.logPortmapFilter());
        mutableParams.setLogTorrentFilter(pref.logTorrentFilter());

        mutableParams.addOnPropertyChangedCallback(paramsCallback);
    }

    LiveData<PagedList<LogEntry>> observeLog()
    {
        return new LivePagedListBuilder<>(sourceFactory, pageConfig)
                .setInitialLoadKey(Integer.MAX_VALUE) /* Start from the last entry */
                .build();
    }

    @Override
    protected void onCleared()
    {
        super.onCleared();

        mutableParams.removeOnPropertyChangedCallback(paramsCallback);
    }

    private final androidx.databinding.Observable.OnPropertyChangedCallback paramsCallback =
            new androidx.databinding.Observable.OnPropertyChangedCallback() {
        @Override
        public void onPropertyChanged(androidx.databinding.Observable sender, int propertyId)
        {
            switch (propertyId) {
                case BR.logging:
                    boolean logging = mutableParams.isLogging();
                    if (!logging) {
                        logPaused = false;
                        recordingStopped = false;
                    }
                    pref.logging(logging);
                    break;
                case BR.logSessionFilter:
                    pref.logSessionFilter(mutableParams.isLogSessionFilter());
                    break;
                case BR.logDhtFilter:
                    pref.logDhtFilter(mutableParams.isLogDhtFilter());
                    break;
                case BR.logPeerFilter:
                    pref.logPeerFilter(mutableParams.isLogPeerFilter());
                    break;
                case BR.logPortmapFilter:
                    pref.logPortmapFilter(mutableParams.isLogPortmapFilter());
                    break;
                case BR.logTorrentFilter:
                    pref.logTorrentFilter(mutableParams.isLogTorrentFilter());
                    break;
            }
        }
    };

    void pauseLog()
    {
        engine.getSessionLogger().pause();
    }

    void pauseLogManually()
    {
        pauseLog();
        logPaused = true;
    }

    void resumeLog()
    {
        engine.getSessionLogger().resume();
    }

    void resumeLogManually()
    {
        resumeLog();
        logPaused = false;
    }

    int getLogEntriesCount()
    {
        return engine.getSessionLogger().getNumEntries();
    }

    boolean logPausedManually()
    {
        return logPaused;
    }

    void startLogRecording()
    {
        recordingStopped = false;

        engine.getSessionLogger().startRecording();
    }

    void stopLogRecording()
    {
        recordingStopped = true;

        engine.getSessionLogger().stopRecording();
    }

    boolean logRecording()
    {
        return !recordingStopped && engine.getSessionLogger().isRecording();
    }

    String getSaveLogFileName()
    {
        String timeStamp = new SimpleDateFormat("MM-dd-yyyy_HH-mm-ss", Locale.getDefault())
                .format(new Date());

        return getApplication().getString(R.string.app_name) + "_log_" + timeStamp + ".txt";
    }

    void saveLog(@NonNull Uri filePath)
    {
        recordingStopped = true;

        Data data = new Data.Builder()
                .putString(SaveLogWorker.TAG_FILE_URI, filePath.toString())
                .putBoolean(SaveLogWorker.TAG_RESUME_AFTER_SAVE, !logPausedManually())
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SaveLogWorker.class)
                .setInputData(data)
                .build();

        WorkManager.getInstance(getApplication()).enqueue(request);
    }
}
