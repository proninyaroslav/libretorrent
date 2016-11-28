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

package org.proninyaroslav.libretorrent.dialogs;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;

/*
 * The base alert dialog fragment. Support customizing the layout, text, buttons, title and style.
 * Don't dismiss when changing the device configuration, unlike the AlertDialog.
 */

public class BaseAlertDialog extends DialogFragment
{
    @SuppressWarnings("unused")
    private static final String TAG = BaseAlertDialog.class.getSimpleName();

    protected static final String TAG_TITLE = "title";
    protected static final String TAG_MESSAGE = "message";
    protected static final String TAG_POS_TEXT = "positive_test";
    protected static final String TAG_NEG_TEXT = "negative_text";
    protected static final String TAG_NEUTRAL_TEXT = "neutral_button";
    protected static final String TAG_RES_ID_VIEW = "res_id_view";

    public interface OnClickListener
    {
        void onPositiveClicked(@Nullable View v);

        void onNegativeClicked(@Nullable View v);

        void onNeutralClicked(@Nullable View v);
    }

    public interface OnDialogShowListener
    {
        void onShow(final AlertDialog dialog);
    }

    /* In the absence of any parameter need set 0 or null */

    public static BaseAlertDialog newInstance(String title, String message, int resIdView,
                                              String positiveText, String negativeText,
                                              String neutralText, Object callback)
    {
        BaseAlertDialog frag = new BaseAlertDialog();

        Bundle args = new Bundle();

        args.putString(TAG_TITLE, title);
        args.putString(TAG_MESSAGE, message);
        args.putString(TAG_POS_TEXT, positiveText);
        args.putString(TAG_NEG_TEXT, negativeText);
        args.putString(TAG_NEUTRAL_TEXT, neutralText);
        args.putInt(TAG_RES_ID_VIEW, resIdView);

        if (callback instanceof Fragment) {
            frag.setTargetFragment((Fragment) callback, 0);
        }

        frag.setArguments(args);

        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        Bundle args = getArguments();

        String title = args.getString(TAG_TITLE);
        String message = args.getString(TAG_MESSAGE);
        String positiveText = args.getString(TAG_POS_TEXT);
        String negativeText = args.getString(TAG_NEG_TEXT);
        String neutralText = args.getString(TAG_NEUTRAL_TEXT);
        int resIdView = args.getInt(TAG_RES_ID_VIEW);

        LayoutInflater i = LayoutInflater.from(getActivity());
        View v = null;
        if (resIdView != 0) {
            v = i.inflate(resIdView, null);
        }

        AlertDialog.Builder dialog = buildDialog(title, message, v,
                positiveText, negativeText, neutralText);

        final AlertDialog alert = dialog.create();

        alert.setOnShowListener(new DialogInterface.OnShowListener()
        {
            @Override
            public void onShow(DialogInterface dialog)
            {
                if (getTargetFragment() != null) {
                    if (getTargetFragment() instanceof OnDialogShowListener) {
                        ((OnDialogShowListener) getTargetFragment()).onShow(alert);
                    }

                } else {
                    if (getActivity() instanceof OnDialogShowListener) {
                        ((OnDialogShowListener) getActivity()).onShow(alert);
                    }
                }
            }
        });

        return alert;
    }

    protected AlertDialog.Builder buildDialog(final String title, final String message,
                                              final View view, final String positiveText,
                                              final String negativeText, String neutralText)
    {
        AlertDialog.Builder dialog;

        dialog = new AlertDialog.Builder(getActivity());

        if (title != null) {
            dialog.setTitle(title);
        }

        if (message != null) {
            dialog.setMessage(message);
        }

        if (view != null) {
            dialog.setView(view);
        }

        if (positiveText != null) {
            dialog.setPositiveButton(positiveText, new DialogInterface.OnClickListener()
            {

                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    if (getTargetFragment() != null) {
                        if (getTargetFragment() instanceof OnClickListener) {
                            ((OnClickListener) getTargetFragment()).onPositiveClicked(view);
                        }

                    } else {
                        if (getActivity() instanceof OnClickListener) {
                            ((OnClickListener) getActivity()).onPositiveClicked(view);
                        }
                    }
                }
            });
        }

        if (negativeText != null) {
            dialog.setNegativeButton(negativeText, new DialogInterface.OnClickListener()
            {

                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    if (getTargetFragment() != null) {
                        if (getTargetFragment() instanceof OnClickListener) {
                            ((OnClickListener) getTargetFragment()).onNegativeClicked(view);
                        }

                    } else {
                        if (getActivity() instanceof OnClickListener) {
                            ((OnClickListener) getActivity()).onNegativeClicked(view);
                        }
                    }
                }
            });
        }

        if (neutralText != null) {
            dialog.setNeutralButton(neutralText, new DialogInterface.OnClickListener()
            {

                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    if (getTargetFragment() != null) {
                        if (getTargetFragment() instanceof OnClickListener) {
                            ((OnClickListener) getTargetFragment()).onNeutralClicked(view);
                        }

                    } else {
                        if (getActivity() instanceof OnClickListener) {
                            ((OnClickListener) getActivity()).onNeutralClicked(view);
                        }
                    }
                }
            });
        }

        return dialog;
    }
}
