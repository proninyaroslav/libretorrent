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

package org.proninyaroslav.libretorrent.ui.tag;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.ui.FragmentCallback;

public class SelectTagActivity extends AppCompatActivity implements FragmentCallback {
    public static final String TAG_EXCLUDE_TAGS_ID = "exclude_tags_id";
    public static final String TAG_RESULT_SELECTED_TAG = "result_selected_tag";

    private static final String TAG_DIALOG = "dialog";

    private SelectTagDialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(Utils.getTranslucentAppTheme(getApplicationContext()));
        super.onCreate(savedInstanceState);


        FragmentManager fm = getSupportFragmentManager();
        dialog = (SelectTagDialog) fm.findFragmentByTag(TAG_DIALOG);
        if (dialog == null) {
            long[] excludeTagsId = getIntent().getLongArrayExtra(TAG_EXCLUDE_TAGS_ID);
            dialog = SelectTagDialog.newInstance(excludeTagsId);
            dialog.show(fm, TAG_DIALOG);
        }
    }

    @Override
    public void onFragmentFinished(
            @NonNull Fragment f,
            Intent intent,
            @NonNull ResultCode code
    ) {
        if (code == ResultCode.OK) {
            setResult(RESULT_OK, intent);
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        dialog.onBackPressed();
    }
}