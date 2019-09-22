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
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;

import com.takisoft.preferencex.PreferenceFragmentCompat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.settings.SettingsRepository;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;
import org.proninyaroslav.libretorrent.core.system.filesystem.FileSystemFacade;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.ui.filemanager.FileManagerDialog;

public class StorageSettingsFragment extends PreferenceFragmentCompat
    implements Preference.OnPreferenceChangeListener
{
    @SuppressWarnings("unused")
    private static final String TAG = StorageSettingsFragment.class.getSimpleName();

    private static final String TAG_DIR_CHOOSER_BIND_PREF = "dir_chooser_bind_pref";
    private static final int DOWNLOAD_DIR_CHOOSE_REQUEST = 1;

    private SettingsRepository pref;
    private FileSystemFacade fs;
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
        fs = SystemFacadeHelper.getFileSystemFacade(context);
        pref = RepositoryHelper.getSettingsRepository(context);

        String keySaveTorrentsIn = getString(R.string.pref_key_save_torrents_in);
        Preference saveTorrentsIn = findPreference(keySaveTorrentsIn);
        if (saveTorrentsIn != null) {
            String path = pref.saveTorrentsIn();
            if (path != null) {
                Uri uri = Uri.parse(path);
                saveTorrentsIn.setSummary(fs.getDirName(uri));
                saveTorrentsIn.setOnPreferenceClickListener((preference) -> {
                    dirChooserBindPref = getString(R.string.pref_key_save_torrents_in);
                    dirChooseDialog(uri);

                    return true;
                });
            }
        }

        String keyMoveAfterDownload = getString(R.string.pref_key_move_after_download);
        SwitchPreferenceCompat moveAfterDownload = findPreference(keyMoveAfterDownload);
        if (moveAfterDownload != null) {
            moveAfterDownload.setChecked(pref.moveAfterDownload());
            bindOnPreferenceChangeListener(moveAfterDownload);
        }

        String keyMoveAfterDownloadIn = getString(R.string.pref_key_move_after_download_in);
        Preference moveAfterDownloadIn = findPreference(keyMoveAfterDownloadIn);
        if (moveAfterDownloadIn != null) {
            String path = pref.moveAfterDownloadIn();
            if (path != null) {
                Uri uri = Uri.parse(path);
                moveAfterDownloadIn.setSummary(fs.getDirName(uri));
                moveAfterDownloadIn.setOnPreferenceClickListener((preference) -> {
                    dirChooserBindPref = getString(R.string.pref_key_move_after_download_in);
                    dirChooseDialog(uri);

                    return true;
                });
            }
        }

        String keySaveTorrentFiles = getString(R.string.pref_key_save_torrent_files);
        SwitchPreferenceCompat saveTorrentFiles = findPreference(keySaveTorrentFiles);
        if (saveTorrentFiles != null) {
            saveTorrentFiles.setChecked(pref.saveTorrentFiles());
            bindOnPreferenceChangeListener(saveTorrentFiles);
        }

        String keySaveTorrentFilesIn = getString(R.string.pref_key_save_torrent_files_in);
        Preference saveTorrentFilesIn = findPreference(keySaveTorrentFilesIn);
        if (saveTorrentFilesIn != null) {
            String path = pref.saveTorrentFilesIn();
            if (path != null) {
                Uri uri = Uri.parse(path);
                saveTorrentFilesIn.setSummary(fs.getDirName(uri));
                saveTorrentFilesIn.setOnPreferenceClickListener((preference) -> {
                    dirChooserBindPref = getString(R.string.pref_key_save_torrent_files_in);
                    dirChooseDialog(uri);

                    return true;
                });
            }
        }

        String keyWatchDir = getString(R.string.pref_key_watch_dir);
        SwitchPreferenceCompat watchDir = findPreference(keyWatchDir);
        if (watchDir != null) {
            watchDir.setChecked(pref.watchDir());
            bindOnPreferenceChangeListener(watchDir);
        }

        String keyDirToWatch = getString(R.string.pref_key_dir_to_watch);
        Preference dirToWatch = findPreference(keyDirToWatch);
        if (dirToWatch != null) {
            String path = pref.dirToWatch();
            if (path != null) {
                Uri uri = Uri.parse(path);
                dirToWatch.setSummary(fs.getDirName(uri));
                dirToWatch.setOnPreferenceClickListener((preference) -> {
                    dirChooserBindPref = getString(R.string.pref_key_dir_to_watch);
                    dirChooseDialog(uri, true);

                    return true;
                });
            }
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

    private void bindOnPreferenceChangeListener(Preference preference)
    {
        preference.setOnPreferenceChangeListener(this);
    }

    private void dirChooseDialog(Uri path)
    {
        dirChooseDialog(path, false);
    }

    private void dirChooseDialog(Uri path, boolean disableSystemFileManager)
    {
        String dirPath = null;
        if (path != null && fs.isFileSystemPath(path))
            dirPath = path.getPath();

        Intent i = new Intent(getActivity(), FileManagerDialog.class);
        FileManagerConfig config = new FileManagerConfig(dirPath,
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

            Preference p = findPreference(dirChooserBindPref);
            if (p == null)
                return;

            if (dirChooserBindPref.equals(getString(R.string.pref_key_dir_to_watch))) {
                pref.dirToWatch(path.toString());

            } else if (dirChooserBindPref.equals(getString(R.string.pref_key_move_after_download_in))) {
                pref.moveAfterDownloadIn(path.toString());

            } else if (dirChooserBindPref.equals(getString(R.string.pref_key_save_torrent_files_in))) {
                pref.saveTorrentFilesIn(path.toString());

            } else if (dirChooserBindPref.equals(getString(R.string.pref_key_save_torrents_in))) {
                pref.saveTorrentsIn(path.toString());
            }
            p.setSummary(fs.getDirName(path));
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue)
    {
        if (preference.getKey().equals(getString(R.string.pref_key_watch_dir))) {
            pref.watchDir((boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_move_after_download))) {
            pref.moveAfterDownload((boolean)newValue);

        } else if (preference.getKey().equals(getString(R.string.pref_key_save_torrent_files))) {
            pref.saveTorrentFiles((boolean)newValue);
        }

        return true;
    }
}
