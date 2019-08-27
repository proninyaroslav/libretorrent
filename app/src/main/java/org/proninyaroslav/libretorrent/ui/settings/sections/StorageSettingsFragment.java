/*
 * Copyright (C) 2016, 2017, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.settings.sections;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.takisoft.preferencex.PreferenceFragmentCompat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.filesystem.FileSystemFacade;
import org.proninyaroslav.libretorrent.core.settings.SettingsManager;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerDialog;

public class StorageSettingsFragment extends PreferenceFragmentCompat
{
    @SuppressWarnings("unused")
    private static final String TAG = StorageSettingsFragment.class.getSimpleName();

    private static final String TAG_DIR_CHOOSER_BIND_PREF = "dir_chooser_bind_pref";

    private static final int DOWNLOAD_DIR_CHOOSE_REQUEST = 1;

    /* Preference that is associated with the current dir selection dialog */
    private String dirChooserBindPref;

    public static StorageSettingsFragment newInstance()
    {
        StorageSettingsFragment fragment = new StorageSettingsFragment();
        fragment.setArguments(new Bundle());

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null)
            dirChooserBindPref = savedInstanceState.getString(TAG_DIR_CHOOSER_BIND_PREF);

        Context context = getActivity().getApplicationContext();
        SharedPreferences pref = SettingsManager.getInstance(context).getPreferences();

        String keySaveTorrentsIn = getString(R.string.pref_key_save_torrents_in);
        Preference saveTorrentsIn = findPreference(keySaveTorrentsIn);
        if (saveTorrentsIn != null) {
            String path = pref.getString(keySaveTorrentsIn, SettingsManager.Default.saveTorrentsIn);
            saveTorrentsIn.setSummary(FileSystemFacade.getDirName(context, Uri.parse(path)));
            saveTorrentsIn.setOnPreferenceClickListener((preference) -> {
                dirChooserBindPref = getString(R.string.pref_key_save_torrents_in);
                dirChooseDialog(pref.getString(dirChooserBindPref, SettingsManager.Default.saveTorrentsIn));

                return true;
            });
        }

        String keyMoveAfterDownload = getString(R.string.pref_key_move_after_download);
        SwitchPreferenceCompat moveAfterDownload = findPreference(keyMoveAfterDownload);
        if (moveAfterDownload != null)
            moveAfterDownload.setChecked(pref.getBoolean(keyMoveAfterDownload,
                                                         SettingsManager.Default.moveAfterDownload));

        String keyMoveAfterDownloadIn = getString(R.string.pref_key_move_after_download_in);
        Preference moveAfterDownloadIn = findPreference(keyMoveAfterDownloadIn);
        if (moveAfterDownloadIn != null) {
            String path = pref.getString(keyMoveAfterDownloadIn, SettingsManager.Default.moveAfterDownloadIn);
            moveAfterDownloadIn.setSummary(FileSystemFacade.getDirName(context, Uri.parse(path)));
            moveAfterDownloadIn.setOnPreferenceClickListener((preference) -> {
                dirChooserBindPref = getString(R.string.pref_key_move_after_download_in);
                dirChooseDialog(pref.getString(dirChooserBindPref, SettingsManager.Default.moveAfterDownloadIn));

                return true;
            });
        }

        String keySaveTorrentFiles = getString(R.string.pref_key_save_torrent_files);
        SwitchPreferenceCompat saveTorrentFiles = findPreference(keySaveTorrentFiles);
        if (saveTorrentFiles != null)
            saveTorrentFiles.setChecked(pref.getBoolean(keySaveTorrentFiles,
                                                        SettingsManager.Default.saveTorrentFiles));

        String keySaveTorrentFilesIn = getString(R.string.pref_key_save_torrent_files_in);
        Preference saveTorrentFilesIn = findPreference(keySaveTorrentFilesIn);
        if (saveTorrentFilesIn != null) {
            String path = pref.getString(keySaveTorrentFilesIn, SettingsManager.Default.saveTorrentFilesIn);
            saveTorrentFilesIn.setSummary(FileSystemFacade.getDirName(context, Uri.parse(path)));
            saveTorrentFilesIn.setOnPreferenceClickListener((preference) -> {
                dirChooserBindPref = getString(R.string.pref_key_save_torrent_files_in);
                dirChooseDialog(pref.getString(dirChooserBindPref, SettingsManager.Default.saveTorrentFilesIn));

                return true;
            });
        }

        String keyWatchDir = getString(R.string.pref_key_watch_dir);
        SwitchPreferenceCompat watchDir = findPreference(keyWatchDir);
        if (watchDir != null)
            watchDir.setChecked(pref.getBoolean(keyWatchDir, SettingsManager.Default.watchDir));

        String keyDirToWatch = getString(R.string.pref_key_dir_to_watch);
        Preference dirToWatch = findPreference(keyDirToWatch);
        if (dirToWatch != null) {
            String path = pref.getString(keyDirToWatch, SettingsManager.Default.dirToWatch);
            dirToWatch.setSummary(FileSystemFacade.getDirName(context, Uri.parse(path)));
            dirToWatch.setOnPreferenceClickListener((Preference preference) -> {
                dirChooserBindPref = getString(R.string.pref_key_dir_to_watch);
                dirChooseDialog(pref.getString(dirChooserBindPref, SettingsManager.Default.dirToWatch), true);

                return true;
            });
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        super.onSaveInstanceState(outState);

        outState.putString(TAG_DIR_CHOOSER_BIND_PREF, dirChooserBindPref);
    }

    @Override
    public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.pref_storage, rootKey);
    }

    private void dirChooseDialog(String path)
    {
        dirChooseDialog(path, false);
    }

    private void dirChooseDialog(String path, boolean disableSystemFileManager)
    {
        Uri uri = Uri.parse(path);
        Intent i = new Intent(getActivity(), FileManagerDialog.class);
        FileManagerConfig config = new FileManagerConfig(
                (FileSystemFacade.isFileSystemPath(uri) ? uri.getPath() : null),
                null,
                FileManagerConfig.DIR_CHOOSER_MODE);
        /* TODO: SAF support */
        config.disableSystemFileManager = disableSystemFileManager;
        i.putExtra(FileManagerDialog.TAG_CONFIG, config);

        startActivityForResult(i, DOWNLOAD_DIR_CHOOSE_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == DOWNLOAD_DIR_CHOOSE_REQUEST && resultCode == Activity.RESULT_OK) {
            if (data.getData() == null || dirChooserBindPref == null)
                return;

            Uri path = data.getData();

            SharedPreferences pref = SettingsManager.getInstance(getActivity().getApplicationContext())
                    .getPreferences();
            pref.edit().putString(dirChooserBindPref, path.toString()).apply();

            Preference p = findPreference(dirChooserBindPref);
            if (p != null)
                p.setSummary(FileSystemFacade.getDirName(getActivity(), path));
        }
    }
}
