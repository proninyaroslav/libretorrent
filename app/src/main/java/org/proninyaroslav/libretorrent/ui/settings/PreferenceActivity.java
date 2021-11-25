/*
 * Copyright (C) 2016-2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceFragmentCompat;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.ui.settings.sections.AnonymousModeSettingsFragment;
import org.proninyaroslav.libretorrent.ui.settings.sections.AppearanceSettingsFragment;
import org.proninyaroslav.libretorrent.ui.settings.sections.BehaviorSettingsFragment;
import org.proninyaroslav.libretorrent.ui.settings.sections.FeedSettingsFragment;
import org.proninyaroslav.libretorrent.ui.settings.sections.LimitationsSettingsFragment;
import org.proninyaroslav.libretorrent.ui.settings.sections.NetworkSettingsFragment;
import org.proninyaroslav.libretorrent.ui.settings.sections.ProxySettingsFragment;
import org.proninyaroslav.libretorrent.ui.settings.sections.SchedulingSettingsFragment;
import org.proninyaroslav.libretorrent.ui.settings.sections.StorageSettingsFragment;
import org.proninyaroslav.libretorrent.ui.settings.sections.StreamingSettingsFragment;

public class PreferenceActivity extends AppCompatActivity
{
    private static final String TAG = PreferenceActivity.class.getSimpleName();

    public static final String TAG_CONFIG = "config";

    private Toolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        setTheme(Utils.getSettingsTheme(getApplicationContext()));
        super.onCreate(savedInstanceState);

        /* Prevent create activity in two pane mode (after resizing window) */
        if (Utils.isLargeScreenDevice(this)) {
            finish();
            return;
        }

        setContentView(R.layout.activity_preference);

        String fragment = null;
        String title = null;
        Intent intent = getIntent();
        if (intent.hasExtra(TAG_CONFIG)) {
            PreferenceActivityConfig config = intent.getParcelableExtra(TAG_CONFIG);
            fragment = config.getFragment();
            title = config.getTitle();
        }

        toolbar = findViewById(R.id.toolbar);
        if (title != null)
            toolbar.setTitle(title);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (fragment != null && savedInstanceState == null)
            setFragment(getFragment(fragment));
    }

    public <F extends PreferenceFragmentCompat> void setFragment(F fragment)
    {
        if (fragment == null)
            return;

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment).commit();
    }

    private <F extends PreferenceFragmentCompat> F getFragment(String fragment)
    {
        if (fragment != null) {
            if (fragment.equals(AppearanceSettingsFragment.class.getSimpleName()))
                return (F)AppearanceSettingsFragment.newInstance();
            else if (fragment.equals(BehaviorSettingsFragment.class.getSimpleName()))
                return (F)BehaviorSettingsFragment.newInstance();
            else if (fragment.equals(StorageSettingsFragment.class.getSimpleName()))
                return (F)StorageSettingsFragment.newInstance();
            else if (fragment.equals(LimitationsSettingsFragment.class.getSimpleName()))
                return (F)LimitationsSettingsFragment.newInstance();
            else if (fragment.equals(NetworkSettingsFragment.class.getSimpleName()))
                return (F)NetworkSettingsFragment.newInstance();
            else if (fragment.equals(ProxySettingsFragment.class.getSimpleName()))
                return (F)ProxySettingsFragment.newInstance();
            else if (fragment.equals(AnonymousModeSettingsFragment.class.getSimpleName()))
                return (F) AnonymousModeSettingsFragment.newInstance();
            else if (fragment.equals(SchedulingSettingsFragment.class.getSimpleName()))
                return (F)SchedulingSettingsFragment.newInstance();
            else if (fragment.equals(FeedSettingsFragment.class.getSimpleName()))
                return (F)FeedSettingsFragment.newInstance();
            else if (fragment.equals(StreamingSettingsFragment.class.getSimpleName()))
                return (F)StreamingSettingsFragment.newInstance();
            else
                return null;
        }

        return null;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == android.R.id.home)
            finish();

        return true;
    }
}
