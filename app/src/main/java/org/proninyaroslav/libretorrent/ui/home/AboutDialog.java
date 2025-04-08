/*
 * Copyright (C) 2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.home;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;
import org.proninyaroslav.libretorrent.databinding.DialogAboutBinding;

public class AboutDialog extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        var activity = requireActivity();
        var binding = DialogAboutBinding.inflate(getLayoutInflater(), null, false);

        var builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.about_title)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.ok, (dialog, which) -> dismiss())
                .setNeutralButton(R.string.about_changelog, (dialog, which) -> openChangelogLink());

        String versionName = SystemFacadeHelper
                .getSystemFacade(activity.getApplicationContext())
                .getAppVersionName();
        if (versionName != null) {
            binding.version.setText(versionName);
        }
        binding.description.setText(
                Html.fromHtml(getString(R.string.about_description), Html.FROM_HTML_MODE_LEGACY)
        );
        binding.description.setMovementMethod(LinkMovementMethod.getInstance());

        return builder.create();
    }

    private void openChangelogLink() {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(getString(R.string.about_changelog_link)));
        startActivity(i);
    }
}
