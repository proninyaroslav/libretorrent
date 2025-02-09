/*
 * Copyright (C) 2016-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.detailtorrent;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.ui.customviews.EmptyListPlaceholder;

public class BlankFragment extends Fragment {
    private static final String ARG_TEXT = "text";

    @StringRes
    private int textRes = -1;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        var v = inflater.inflate(R.layout.fragment_blank, container, false);

        var args = getArguments();
        if (args != null) {
            textRes = getArguments().getInt(ARG_TEXT);
        }

        EmptyListPlaceholder placeholder = v.findViewById(R.id.placeholder);
        if (textRes != -1) {
            placeholder.setText(textRes);
        }

        return v;
    }
}
