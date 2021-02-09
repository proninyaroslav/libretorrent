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

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.model.data.entity.TagInfo;
import org.proninyaroslav.libretorrent.core.storage.TagRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.Flowable;

public class SelectTagViewModel extends AndroidViewModel {
    private TagRepository tagRepo;
    private Set<Long> excludeTagsId;

    public SelectTagViewModel(@NonNull Application application) {
        super(application);

        tagRepo = RepositoryHelper.getTagRepository(application);
    }

    public void setExcludeTagsId(long[] tagsId) {
        if (tagsId == null) {
            excludeTagsId = null;
        } else {
            excludeTagsId = new HashSet<>();
            for (long tagId : tagsId) {
                excludeTagsId.add(tagId);
            }
        }
    }

    public boolean filterExcludeTags(@NonNull TagInfo info) {
        return excludeTagsId == null || !excludeTagsId.contains(info.id);
    }

    Flowable<List<TagInfo>> observeTags() {
        return tagRepo.observeAll();
    }
}
