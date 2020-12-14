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

package org.proninyaroslav.libretorrent.service;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.exception.UnknownUriException;
import org.proninyaroslav.libretorrent.core.logger.Logger;
import org.proninyaroslav.libretorrent.core.model.TorrentEngine;
import org.proninyaroslav.libretorrent.core.system.FileDescriptorWrapper;
import org.proninyaroslav.libretorrent.core.system.FileSystemFacade;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;

import java.io.FileOutputStream;
import java.io.IOException;

public class SaveLogWorker extends Worker
{
    private static final String TAG = SaveLogWorker.class.getSimpleName();

    public static final String TAG_FILE_URI = "file_uri";
    public static final String TAG_RESUME_AFTER_SAVE = "resume_after_save";

    private Context appContext;
    private TorrentEngine engine;
    private FileSystemFacade fs;
    Handler handler = new Handler(Looper.getMainLooper());

    public SaveLogWorker(@NonNull Context context, @NonNull WorkerParameters params)
    {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork()
    {
        appContext = getApplicationContext();
        engine = TorrentEngine.getInstance(appContext);
        fs = SystemFacadeHelper.getFileSystemFacade(appContext);

        Data data = getInputData();
        String filePath = data.getString(TAG_FILE_URI);
        if (filePath == null) {
            Log.e(TAG, "Cannot save log: file path is null");
            showFailToast();

            return Result.failure();
        }

        boolean resume = data.getBoolean(TAG_RESUME_AFTER_SAVE, true);

        return saveLog(Uri.parse(filePath), resume);
    }

    private Result saveLog(Uri filePath, boolean resume)
    {
        Logger logger = engine.getSessionLogger();
        logger.pause();

        try (FileDescriptorWrapper w = fs.getFD(filePath);
             FileOutputStream fout = new FileOutputStream(w.open("rw"))) {

            if (logger.isRecording())
                logger.stopRecording(fout, true);
            else
                logger.write(fout, true);

            showSuccessToast(fs.getFilePath(filePath));

        } catch (IOException | UnknownUriException e) {
            Log.e(TAG, "Cannot save log: " + Log.getStackTraceString(e));

            return Result.failure();

        } finally {
            if (resume)
                logger.resume();
        }

        return Result.success();
    }

    private void showFailToast()
    {
        handler.post(() -> Toast.makeText(appContext,
                R.string.journal_save_log_failed,
                Toast.LENGTH_SHORT)
                .show()
        );
    }

    private void showSuccessToast(String fileName)
    {
        handler.post(() -> Toast.makeText(appContext,
                appContext.getString(R.string.journal_save_log_success, fileName),
                Toast.LENGTH_LONG)
                .show()
        );
    }
}
