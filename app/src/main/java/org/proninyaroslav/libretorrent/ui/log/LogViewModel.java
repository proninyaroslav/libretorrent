/*
 * Copyright (C) 2020-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.app.Activity;
import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.databinding.library.baseAdapters.BR;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.ViewModelKt;
import androidx.paging.Pager;
import androidx.paging.PagingConfig;
import androidx.paging.PagingData;
import androidx.paging.rxjava3.PagingRx;
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

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;

public class LogViewModel extends AndroidViewModel {
    private static final int PAGE_SIZE = 50;
    private static final int PREFETCH_SIZE = 50;

    private final TorrentEngine engine;
    private final SettingsRepository pref;
    public LogMutableParams mutableParams = new LogMutableParams();
    private final LogSourceFactory sourceFactory;
    private final PagingConfig pageConfig = new PagingConfig(PAGE_SIZE, PREFETCH_SIZE, false);
    private boolean logPaused;
    private boolean recordingStopped;

    public LogViewModel(@NonNull Application application) {
        super(application);

        engine = TorrentEngine.getInstance(application);
        pref = RepositoryHelper.getSettingsRepository(application);

        Logger sessionLogger = engine.getSessionLogger();
        logPaused = sessionLogger.isPaused();
        recordingStopped = !sessionLogger.isRecording();
        sourceFactory = new LogSourceFactory(sessionLogger);

        initMutableParams();
    }

    private void initMutableParams() {
        mutableParams.setLogging(pref.logging());
        mutableParams.setLogSessionFilter(pref.logSessionFilter());
        mutableParams.setLogDhtFilter(pref.logDhtFilter());
        mutableParams.setLogPeerFilter(pref.logPeerFilter());
        mutableParams.setLogPortmapFilter(pref.logPortmapFilter());
        mutableParams.setLogTorrentFilter(pref.logTorrentFilter());

        mutableParams.addOnPropertyChangedCallback(paramsCallback);
    }

    public Flowable<PagingData<LogEntry>> observeLog() {
        var viewModelScope = ViewModelKt.getViewModelScope(this);
        Pager<Integer, LogEntry> pager = new Pager<>(pageConfig, sourceFactory::create);

        var flowable = PagingRx.getFlowable(pager);
        PagingRx.cachedIn(flowable, viewModelScope);

        return flowable;
    }

    public Observable<Logger.DataSetChange> observeDataSetChanged() {
        return engine.getSessionLogger().observeDataSetChanged();
    }

    @Override
    protected void onCleared() {
        super.onCleared();

        mutableParams.removeOnPropertyChangedCallback(paramsCallback);
    }

    private final androidx.databinding.Observable.OnPropertyChangedCallback paramsCallback =
            new androidx.databinding.Observable.OnPropertyChangedCallback() {
                @Override
                public void onPropertyChanged(androidx.databinding.Observable sender, int propertyId) {
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

    void pauseLog() {
        engine.getSessionLogger().pause();
    }

    void pauseLogManually() {
        pauseLog();
        logPaused = true;
    }

    void resumeLog() {
        engine.getSessionLogger().resume();
    }

    void resumeLogManually() {
        resumeLog();
        logPaused = false;
    }

    int getLogEntriesCount() {
        return engine.getSessionLogger().getNumEntries();
    }

    boolean logPausedManually() {
        return logPaused;
    }

    void startLogRecording() {
        recordingStopped = false;

        engine.getSessionLogger().startRecording();
    }

    void stopLogRecording() {
        recordingStopped = true;

        engine.getSessionLogger().stopRecording();
    }

    boolean logRecording() {
        return !recordingStopped && engine.getSessionLogger().isRecording();
    }

    String getSaveLogFileName() {
        String timeStamp = new SimpleDateFormat("MM-dd-yyyy_HH-mm-ss", Locale.getDefault())
                .format(new Date());

        return getApplication().getString(R.string.app_name) + "_log_" + timeStamp + ".txt";
    }

    void saveLog(@NonNull Uri filePath) {
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

    boolean copyLogEntryToClipboard(@NonNull LogEntry entry) {
        ClipboardManager clipboard = (ClipboardManager) getApplication().getSystemService(Activity.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return false;
        }

        ClipData clip;
        clip = ClipData.newPlainText("Log entry", entry.toString());
        clipboard.setPrimaryClip(clip);

        return true;
    }
}
