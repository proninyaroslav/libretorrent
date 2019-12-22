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

import androidx.annotation.NonNull;
import androidx.databinding.ObservableBoolean;
import androidx.databinding.ObservableField;
import androidx.lifecycle.ViewModel;

import org.proninyaroslav.libretorrent.core.exception.NormalizeUrlException;
import org.proninyaroslav.libretorrent.core.urlnormalizer.NormalizeUrl;
import org.proninyaroslav.libretorrent.core.utils.Utils;

public class AddLinkViewModel extends ViewModel
{
    public ObservableBoolean showClipboardButton = new ObservableBoolean();
    public ObservableField<String> link = new ObservableField<>();

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
