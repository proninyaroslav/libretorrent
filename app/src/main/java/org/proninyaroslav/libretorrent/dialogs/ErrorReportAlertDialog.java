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

import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.github.aakira.expandablelayout.ExpandableLayoutListener;
import com.github.aakira.expandablelayout.ExpandableLinearLayout;
import com.github.aakira.expandablelayout.Utils;

import org.proninyaroslav.libretorrent.R;

public class ErrorReportAlertDialog extends BaseAlertDialog
{
    @SuppressWarnings("unused")
    private static final String TAG = ErrorReportAlertDialog.class.getSimpleName();

    protected static final String TAG_DETAIL_ERROR = "detail_error";

    /* In the absence of any parameter need set 0 or null */

    public static ErrorReportAlertDialog newInstance(Context context,
                                                     String title, String message,
                                                     String detailError, Object callback)
    {
        ErrorReportAlertDialog frag = new ErrorReportAlertDialog();

        Bundle args = new Bundle();

        args.putString(TAG_TITLE, title);
        args.putString(TAG_MESSAGE, message);
        args.putString(TAG_POS_TEXT, context.getString(R.string.report));
        args.putString(TAG_NEG_TEXT, context.getString(R.string.cancel));
        args.putInt(TAG_RES_ID_VIEW, R.layout.dialog_error);
        args.putString(TAG_DETAIL_ERROR, detailError);

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
        String negativeText = args.getString(TAG_NEG_TEXT);
        String positiveText = args.getString(TAG_POS_TEXT);
        String detailError = args.getString(TAG_DETAIL_ERROR);
        int resIdView = args.getInt(TAG_RES_ID_VIEW);

        View v = null;
        if (detailError != null && !TextUtils.isEmpty(detailError)) {
            LayoutInflater i = LayoutInflater.from(getActivity());
            if (resIdView != 0) {
                v = i.inflate(resIdView, null);
            }

            initLayoutView(v);

            if (v != null) {
                TextView detailErrorView = (TextView) v.findViewById(R.id.detail_error);
                detailErrorView.setText(detailError);
            }
        }

        AlertDialog.Builder dialog = buildDialog(title, message, v,
                positiveText, negativeText, null);

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

    private ObjectAnimator createRotateAnimator(final View target, final float from, final float to)
    {
        ObjectAnimator animator = ObjectAnimator.ofFloat(target, "rotation", from, to);
        animator.setDuration(300);
        animator.setInterpolator(Utils.createInterpolator(Utils.LINEAR_INTERPOLATOR));

        return animator;
    }

    private void initLayoutView(View v)
    {
        if (v != null) {
            final RelativeLayout expandableSpinner =
                    (RelativeLayout) v.findViewById(R.id.expandable_spinner);
            final RelativeLayout expandButton = (RelativeLayout) v.findViewById(R.id.expand_button);
            final ExpandableLinearLayout expandableLayout =
                    (ExpandableLinearLayout) v.findViewById(R.id.expandable_layout);

            expandableLayout.setListener(new ExpandableLayoutListener() {
                @Override
                public void onAnimationStart() {
                    /* Nothing */
                }

                @Override
                public void onAnimationEnd() {
                    /* Nothing */
                }

                @Override
                public void onPreOpen() {
                    createRotateAnimator(expandButton, 0f, 180f).start();
                }

                @Override
                public void onPreClose() {
                    createRotateAnimator(expandButton, 180f, 0f).start();
                }

                @Override
                public void onOpened() {
                    /* Nothing */
                }

                @Override
                public void onClosed() {
                    /* Nothing */
                }
            });

            expandableSpinner.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    expandableLayout.toggle();
                }
            });
        }
    }
}
