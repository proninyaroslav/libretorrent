/*
 * Copyright (C) 2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.core.utils.Utils;

public class StoragePermissionManager {
    private ActivityResultLauncher<Intent> manageExternalStoragePermission;
    private ActivityResultLauncher<String> storagePermission;
    private final Context appContext;
    private SettingsRepository pref;

    public StoragePermissionManager(
            @NonNull ComponentActivity activity,
            @NonNull Callback callback
    ) {
        appContext = activity.getApplicationContext();
        pref = RepositoryHelper.getSettingsRepository(appContext);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manageExternalStoragePermission = activity.registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> callback.onResult(
                            Utils.checkStoragePermission(appContext),
                            pref.askManageAllFilesPermission()
                    )
            );
        } else {
            storagePermission = activity.registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> callback.onResult(
                            isGranted,
                            Utils.shouldRequestStoragePermission(activity)
                    )
            );
        }
    }

    public void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!pref.askManageAllFilesPermission()) {
                return;
            }
            var uri = Uri.fromParts("package", appContext.getPackageName(), null);
            var i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    .setData(uri);
            manageExternalStoragePermission.launch(i);
        } else {
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
    }

    public void setDoNotAsk(boolean doNotAsk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pref.askManageAllFilesPermission(!doNotAsk);
        }
    }

    public boolean checkPermissions() {
        return Utils.checkStoragePermission(appContext);
    }

    public interface Callback {
        void onResult(boolean isGranted, boolean shouldRequestStoragePermission);
    }
}
