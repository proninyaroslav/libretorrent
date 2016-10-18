/*
 * Copyright (C) 2016 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.preference.Preference;

import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat;
import com.takisoft.fix.support.v7.preference.SwitchPreferenceCompat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.dialogs.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.dialogs.filemanager.FileManagerDialog;

/*
 * TODO: add folder scanner
 */

public class StorageSettingsFragment extends PreferenceFragmentCompat
        implements
        Preference.OnPreferenceChangeListener
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

        if (savedInstanceState != null) {
            dirChooserBindPref = savedInstanceState.getString(TAG_DIR_CHOOSER_BIND_PREF);
        }

        SettingsManager pref = new SettingsManager(getActivity().getApplicationContext());

        String keySaveTorrentsIn = getString(R.string.pref_key_save_torrents_in);
        Preference saveTorrentsIn = findPreference(keySaveTorrentsIn);
        saveTorrentsIn.setSummary(pref.getString(keySaveTorrentsIn, ""));
        saveTorrentsIn.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
        {
            @Override
            public boolean onPreferenceClick(Preference preference)
            {
                SettingsManager pref = new SettingsManager(getActivity().getApplicationContext());
                dirChooserBindPref = getString(R.string.pref_key_save_torrents_in);
                dirChooseDialog(pref.getString(dirChooserBindPref, ""));

                return true;
            }
        });

        String keyMoveAfterDownload = getString(R.string.pref_key_move_after_download);
        SwitchPreferenceCompat moveAfterDownload =
                (SwitchPreferenceCompat) findPreference(keyMoveAfterDownload);
        moveAfterDownload.setChecked(pref.getBoolean(keyMoveAfterDownload, false));
        bindOnPreferenceChangeListener(moveAfterDownload);

        String keyMoveAfterDownloadIn = getString(R.string.pref_key_move_after_download_in);
        Preference moveAfterDownloadIn = findPreference(keyMoveAfterDownloadIn);
        moveAfterDownloadIn.setSummary(pref.getString(keyMoveAfterDownloadIn, ""));
        moveAfterDownloadIn.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener()
        {
            @Override
            public boolean onPreferenceClick(Preference preference)
            {
                SettingsManager pref = new SettingsManager(getActivity().getApplicationContext());
                dirChooserBindPref = getString(R.string.pref_key_move_after_download_in);
                dirChooseDialog(pref.getString(dirChooserBindPref, ""));

                return true;
            }
        });

        String keyDeleteTorrentFile = getString(R.string.pref_key_delete_torrent_file);
        SwitchPreferenceCompat deleteTorrentFile =
                (SwitchPreferenceCompat) findPreference(keyDeleteTorrentFile);
        deleteTorrentFile.setChecked(pref.getBoolean(keyDeleteTorrentFile, false));
        bindOnPreferenceChangeListener(deleteTorrentFile);
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
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
        Intent i = new Intent(getActivity(), FileManagerDialog.class);
        FileManagerConfig config = new FileManagerConfig(path, null, null, FileManagerConfig.DIR_CHOOSER_MODE);
        i.putExtra(FileManagerDialog.TAG_CONFIG, config);

        startActivityForResult(i, DOWNLOAD_DIR_CHOOSE_REQUEST);
    }

    private void bindOnPreferenceChangeListener(Preference preference)
    {
        preference.setOnPreferenceChangeListener(this);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue)
    {
        if (preference instanceof SwitchPreferenceCompat) {
            SettingsManager pref = new SettingsManager(getActivity().getApplicationContext());
            pref.put(preference.getKey(), (boolean) newValue);
        }

        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == DOWNLOAD_DIR_CHOOSE_REQUEST && resultCode == Activity.RESULT_OK) {
            if (data.hasExtra(FileManagerDialog.TAG_RETURNED_PATH) && dirChooserBindPref != null) {
                String path = data.getStringExtra(FileManagerDialog.TAG_RETURNED_PATH);

                SettingsManager pref = new SettingsManager(getActivity().getApplicationContext());
                pref.put(dirChooserBindPref, path);

                Preference p = findPreference(dirChooserBindPref);
                p.setSummary(path);
            }
        }
    }
}
