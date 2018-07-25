/*
 * Copyright (C) 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.dialogs;

import android.app.Dialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.design.widget.TextInputEditText;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import org.proninyaroslav.libretorrent.R;

public class AddRssChannelDialog extends BaseAlertDialog
{
    @SuppressWarnings("unused")
    private static final String TAG = AddRssChannelDialog.class.getSimpleName();

    /* In the absence of any parameter need set 0 or null */

    public static AddRssChannelDialog newInstance(Context context, String title,
                                                  String positiveText, Object callback)
    {
        AddRssChannelDialog frag = new AddRssChannelDialog();

        Bundle args = new Bundle();

        args.putString(TAG_TITLE, title);
        args.putString(TAG_POS_TEXT, positiveText);
        args.putString(TAG_NEG_TEXT, context.getString(R.string.cancel));
        args.putInt(TAG_RES_ID_VIEW, R.layout.dialog_add_feed_channel);

        if (callback instanceof Fragment)
            frag.setTargetFragment((Fragment) callback, 0);

        frag.setArguments(args);

        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        Bundle args = getArguments();

        String title = args.getString(TAG_TITLE);
        String negativeText = args.getString(TAG_NEG_TEXT);
        String positiveText = args.getString(TAG_POS_TEXT);
        int resIdView = args.getInt(TAG_RES_ID_VIEW);

        LayoutInflater i = LayoutInflater.from(getActivity());
        View v = null;
        if (resIdView != 0)
            v = i.inflate(resIdView, null);
        if (v != null) {
            final TextInputEditText filterField = v.findViewById(R.id.feed_channel_filter);
            final CheckBox isRegexField = v.findViewById(R.id.feed_use_regex);
            CheckBox autoDownloadField = v.findViewById(R.id.feed_auto_download);
            autoDownloadField.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
            {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
                {
                    if (!isChecked)
                        filterField.clearFocus();
                    filterField.setEnabled(isChecked);
                    isRegexField.setEnabled(isChecked);
                }
            });
        }

        AlertDialog.Builder dialog = buildDialog(title, null, v, positiveText, negativeText, null);
        final AlertDialog alert = dialog.create();
        alert.setOnShowListener(new DialogInterface.OnShowListener()
        {
            @Override
            public void onShow(DialogInterface dialog)
            {
                if (getTargetFragment() != null) {
                    if (getTargetFragment() instanceof OnDialogShowListener)
                        ((OnDialogShowListener) getTargetFragment()).onShow(alert);

                } else {
                    if (getActivity() instanceof OnDialogShowListener)
                        ((OnDialogShowListener) getActivity()).onShow(alert);
                }
            }
        });

        return alert;
    }
}
