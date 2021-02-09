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

package org.proninyaroslav.libretorrent.ui.addtag;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import android.content.Intent;
import android.os.Bundle;

import com.jaredrummler.android.colorpicker.ColorPickerDialogListener;

import org.proninyaroslav.libretorrent.core.model.data.entity.TagInfo;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.ui.FragmentCallback;

public class AddTagActivity extends AppCompatActivity
        implements FragmentCallback, ColorPickerDialogListener {
    private static final String TAG_DIALOG = "dialog";

    public static final String TAG_INIT_INFO = "init_info";

    private AddTagDialog dialog;
    private AddTagViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(Utils.getTranslucentAppTheme(getApplicationContext()));
        super.onCreate(savedInstanceState);

        ViewModelProvider provider = new ViewModelProvider(this);
        viewModel = provider.get(AddTagViewModel.class);

        FragmentManager fm = getSupportFragmentManager();
        dialog = (AddTagDialog) fm.findFragmentByTag(TAG_DIALOG);
        if (dialog == null) {
            TagInfo initInfo = getIntent().getParcelableExtra(TAG_INIT_INFO);
            dialog = AddTagDialog.newInstance(initInfo);
            dialog.show(fm, TAG_DIALOG);
        }
    }

    @Override
    public void onFragmentFinished(
            @NonNull Fragment f,
            Intent intent,
            @NonNull ResultCode code
    ) {
        finish();
    }

    @Override
    public void onBackPressed() {
        dialog.onBackPressed();
    }

    @Override
    public void onColorSelected(int dialogId, int color) {
        viewModel.state.setColor(color);
    }

    @Override
    public void onDialogDismissed(int dialogId) {
    }
}