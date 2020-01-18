/*
 * Copyright (C) 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.addlink;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableBoolean;
import androidx.databinding.ObservableField;
import androidx.lifecycle.AndroidViewModel;

import org.proninyaroslav.libretorrent.core.exception.NormalizeUrlException;
import org.proninyaroslav.libretorrent.core.urlnormalizer.NormalizeUrl;
import org.proninyaroslav.libretorrent.core.utils.Utils;

import java.util.List;

public class AddLinkViewModel extends AndroidViewModel
{
    public ObservableBoolean showClipboardButton = new ObservableBoolean();
    public ObservableField<String> link = new ObservableField<>();

    public AddLinkViewModel(@NonNull Application application)
    {
        super(application);
    }

    void initLinkFromClipboard()
    {
        List<CharSequence> clipboard = Utils.getClipboardText(getApplication());
        if (clipboard.isEmpty())
            return;

        String firstItem = clipboard.get(0).toString();
        String c = firstItem.toLowerCase();
        if (c.startsWith(Utils.MAGNET_PREFIX) ||
            c.startsWith(Utils.HTTP_PREFIX) ||
            Utils.isHash(firstItem))
        {
            link.set(firstItem);
        }
    }

    String normalizeUrl(@NonNull String link) throws NormalizeUrlException
    {
        if (Utils.isHash(link)) {
            link = Utils.normalizeMagnetHash(link);

        } else if (!link.toLowerCase().startsWith(Utils.MAGNET_PREFIX)) {
            NormalizeUrl.Options options = new NormalizeUrl.Options();
            options.decode = false;
            link = NormalizeUrl.normalize(link, options);
        }

        return link;
    }
}
