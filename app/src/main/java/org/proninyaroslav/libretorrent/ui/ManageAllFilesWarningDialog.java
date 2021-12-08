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

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.utils.Utils;

public class ManageAllFilesWarningDialog extends BaseAlertDialog {
    public static ManageAllFilesWarningDialog newInstance() {
        ManageAllFilesWarningDialog frag = new ManageAllFilesWarningDialog();

        Bundle args = new Bundle();
        frag.setArguments(args);

        return frag;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        String title = getString(R.string.manage_all_files_warning_dialog_title);
        String message = getString(R.string.manage_all_files_warning_dialog_description)
                + Utils.getLineSeparator() + getString(R.string.project_page);
        String positiveText = getString(R.string.ok);
        var i = LayoutInflater.from(getActivity());
        var v = i.inflate(R.layout.dialog_linkify_text, null);
        var messageView = (TextView) v.findViewById(R.id.message);
        messageView.setText(message);

        return buildDialog(
                title,
                null,
                v,
                positiveText,
                null,
                null,
                true
        );
    }
}
