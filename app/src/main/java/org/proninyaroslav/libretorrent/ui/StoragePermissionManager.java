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
import android.content.Context;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import org.proninyaroslav.libretorrent.core.utils.Utils;

public class StoragePermissionManager {
    private ActivityResultLauncher<String> storagePermission;
    private final Context appContext;

    public StoragePermissionManager(
            @NonNull ComponentActivity activity,
            @NonNull Callback callback
    ) {
        appContext = activity.getApplicationContext();
        storagePermission = activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> callback.onResult(
                        isGranted,
                        Utils.shouldRequestStoragePermission(activity)
                )
        );
    }

    public void requestPermissions() {
        storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    public boolean checkPermissions() {
        return Utils.checkStoragePermission(appContext);
    }

    public interface Callback {
        void onResult(boolean isGranted, boolean shouldRequestStoragePermission);
    }
}
