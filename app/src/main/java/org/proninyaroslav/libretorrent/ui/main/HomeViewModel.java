/*
 * Copyright (C) 2019-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.main;

import android.app.Application;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory;

import org.proninyaroslav.libretorrent.core.RepositoryHelper;
import org.proninyaroslav.libretorrent.core.filter.TorrentFilter;
import org.proninyaroslav.libretorrent.core.filter.TorrentFilterCollection;
import org.proninyaroslav.libretorrent.core.model.TorrentEngine;
import org.proninyaroslav.libretorrent.core.model.TorrentInfoProvider;
import org.proninyaroslav.libretorrent.core.model.data.TorrentInfo;
import org.proninyaroslav.libretorrent.core.model.data.entity.TagInfo;
import org.proninyaroslav.libretorrent.core.sorting.TorrentSorting;
import org.proninyaroslav.libretorrent.core.sorting.TorrentSortingComparator;
import org.proninyaroslav.libretorrent.core.storage.TagRepository;
import org.proninyaroslav.libretorrent.ui.main.drawer.model.DrawerDateAddedFilter;
import org.proninyaroslav.libretorrent.ui.main.drawer.model.DrawerSort;
import org.proninyaroslav.libretorrent.ui.main.drawer.model.DrawerSortDirection;
import org.proninyaroslav.libretorrent.ui.main.drawer.model.DrawerStatusFilter;
import org.proninyaroslav.libretorrent.ui.main.drawer.model.DrawerTagFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.subjects.PublishSubject;

public class HomeViewModel extends AndroidViewModel {
    private static final String TAG = HomeViewModel.class.getSimpleName();

    private static final long FORCE_FILTER_DEBOUNCE_TIME = 500; // ms

    private final TorrentInfoProvider stateProvider;
    private final TorrentEngine engine;
    private TorrentSortingComparator sorting = new TorrentSortingComparator(
            new TorrentSorting(TorrentSorting.SortingColumns.none, TorrentSorting.Direction.ASC));
    private List<TorrentFilter> statusFilter = List.of(TorrentFilterCollection.all());
    private List<TorrentFilter> dateAddedFilter = List.of(TorrentFilterCollection.all());
    private TorrentFilter tagFilter = TorrentFilterCollection.all();
    private final PublishSubject<Boolean> forceSortAndFilter = PublishSubject.create();
    private final PublishSubject<Boolean> forceSearch = PublishSubject.create();
    private final TagRepository tagRepo;
    private final Moshi moshiDrawer = new Moshi.Builder()
            .add(PolymorphicJsonAdapterFactory.of(DrawerTagFilter.class, "type")
                    .withSubtype(DrawerTagFilter.NoTags.class, "no_tags")
                    .withSubtype(DrawerTagFilter.Item.class, "item")
            )
            .build();

    private String searchQuery;
    private final TorrentFilter searchFilter = (state) -> {
        if (TextUtils.isEmpty(searchQuery))
            return true;

        String filterPattern = searchQuery.toLowerCase().trim();

        return state.name.toLowerCase().contains(filterPattern);
    };

    public HomeViewModel(@NonNull Application application) {
        super(application);

        stateProvider = TorrentInfoProvider.getInstance(application);
        engine = TorrentEngine.getInstance(application);
        tagRepo = RepositoryHelper.getTagRepository(application);
    }

    public Flowable<List<TorrentInfo>> observeAllTorrentsInfo() {
        return stateProvider.observeInfoList();
    }

    public Single<List<TorrentInfo>> getAllTorrentsInfoSingle() {
        return stateProvider.getInfoListSingle();
    }

    public Flowable<String> observeTorrentsDeleted() {
        return stateProvider.observeTorrentsDeleted();
    }

    public void setSort(@NonNull TorrentSortingComparator sorting, boolean force) {
        this.sorting = sorting;
        if (force && !sorting.sorting().getColumnName().equals(TorrentSorting.SortingColumns.none.name())) {
            forceSortAndFilter.onNext(true);
        }
    }

    public TorrentSortingComparator getSorting() {
        return sorting;
    }

    public void setStatusFilter(@NonNull List<TorrentFilter> statusFilter, boolean force) {
        this.statusFilter = statusFilter;
        if (force) {
            forceSortAndFilter.onNext(true);
        }
    }

    public void setDateAddedFilter(@NonNull List<TorrentFilter> dateAddedFilter, boolean force) {
        this.dateAddedFilter = dateAddedFilter;
        if (force) {
            forceSortAndFilter.onNext(true);
        }
    }

    public void setTagFilter(@NonNull TorrentFilter tagFilter, boolean force) {
        this.tagFilter = tagFilter;
        if (force) {
            forceSortAndFilter.onNext(true);
        }
    }

    public TorrentFilter getFilter() {
        return (state) -> {
            var status = statusFilter.isEmpty() || statusFilter.stream()
                    .map((filter) -> {
                        try {
                            return filter.test(state);
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .reduce(Boolean.FALSE, Boolean::logicalOr);
            var dateAdded = dateAddedFilter.isEmpty() || dateAddedFilter.stream()
                    .map((filter) -> {
                        try {
                            return filter.test(state);
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .reduce(Boolean.FALSE, Boolean::logicalOr);
            return status && dateAdded && tagFilter.test(state);
        };
    }

    public TorrentFilter getSearchFilter() {
        return searchFilter;
    }


    public void setSearchQuery(@Nullable String searchQuery) {
        this.searchQuery = searchQuery;
        forceSearch.onNext(true);
    }

    public void resetSearch() {
        setSearchQuery(null);
    }

    public Observable<Boolean> observeForceSortAndFilter() {
        return forceSortAndFilter.debounce(FORCE_FILTER_DEBOUNCE_TIME, TimeUnit.MILLISECONDS);
    }

    public Observable<Boolean> observeForceSearch() {
        return forceSearch;
    }

    public void pauseResumeTorrent(@NonNull String id) {
        engine.pauseResumeTorrent(id);
    }

    public void deleteTorrents(@NonNull List<String> ids, boolean withFiles) {
        engine.deleteTorrents(ids, withFiles);
    }

    public void forceRecheckTorrents(@NonNull List<String> ids) {
        engine.forceRecheckTorrents(ids);
    }

    public void forceAnnounceTorrents(@NonNull List<String> ids) {
        engine.forceAnnounceTorrents(ids);
    }

    public Flowable<Boolean> observeNeedStartEngine() {
        return engine.observeNeedStartEngine();
    }

    public void startEngine() {
        engine.start();
    }

    public void restartForegroundNotification() {
        engine.restartForegroundNotification();
    }

    public void requestStopEngine() {
        engine.requestStop();
    }

    public void stopEngine() {
        engine.forceStop();
    }

    public void pauseAll() {
        engine.pauseAll();
    }

    public void resumeAll() {
        engine.resumeAll();
    }

    public Flowable<List<TagInfo>> observeTags() {
        return tagRepo.observeAll();
    }

    public Completable deleteTag(@NonNull TagInfo info) {
        return Completable.fromRunnable(() -> tagRepo.delete(info));
    }

    public String encodeDrawerSorting(@NonNull DrawerSort sort) {
        return moshiDrawer.adapter(DrawerSort.class).toJson(sort);
    }

    public DrawerSort decodeDrawerSorting(@Nullable String json) {
        if (json == null) {
            return DrawerSort.DateAdded;
        } else {
            try {
                return moshiDrawer.adapter(DrawerSort.class).fromJson(json);
            } catch (IOException e) {
                Log.e(TAG, "Unable to decode sort", e);
                return DrawerSort.DateAdded;
            }
        }
    }

    public String encodeDrawerStatusFilter(@NonNull List<DrawerStatusFilter> filters) {
        var type = Types.newParameterizedType(Set.class, DrawerStatusFilter.class);
        return moshiDrawer.adapter(type).toJson(filters);
    }

    public Set<DrawerStatusFilter> decodeDrawerStatusFilter(@Nullable String json) {
        var type = Types.newParameterizedType(Set.class, DrawerStatusFilter.class);
        JsonAdapter<Set<DrawerStatusFilter>> adapter = moshiDrawer.adapter(type);
        if (json == null) {
            return Collections.emptySet();
        } else {
            try {
                return adapter.fromJson(json);
            } catch (IOException e) {
                Log.e(TAG, "Unable to decode status filters", e);
                return Collections.emptySet();
            }
        }
    }

    public String encodeDrawerSortingDirection(@NonNull DrawerSortDirection direction) {
        return moshiDrawer.adapter(DrawerSortDirection.class).toJson(direction);
    }

    public DrawerSortDirection decodeDrawerSortingDirection(@Nullable String json) {
        if (json == null) {
            return DrawerSortDirection.Descending;
        } else {
            try {
                return moshiDrawer.adapter(DrawerSortDirection.class).fromJson(json);
            } catch (IOException e) {
                Log.e(TAG, "Unable to decode sort direction", e);
                return DrawerSortDirection.Descending;
            }
        }
    }

    public String encodeDrawerDateAddedFilter(@NonNull List<DrawerDateAddedFilter> filters) {
        var type = Types.newParameterizedType(Set.class, DrawerDateAddedFilter.class);
        return moshiDrawer.adapter(type).toJson(filters);
    }

    public Set<DrawerDateAddedFilter> decodeDrawerDateAddedFilter(@Nullable String json) {
        var type = Types.newParameterizedType(Set.class, DrawerDateAddedFilter.class);
        JsonAdapter<Set<DrawerDateAddedFilter>> adapter = moshiDrawer.adapter(type);
        if (json == null) {
            return Collections.emptySet();
        } else {
            try {
                return adapter.fromJson(json);
            } catch (IOException e) {
                Log.e(TAG, "Unable to decode date added filters", e);
                return Collections.emptySet();
            }
        }
    }

    public String encodeDrawerTagFilter(@NonNull List<DrawerTagFilter> filters) {
        var type = Types.newParameterizedType(Set.class, DrawerTagFilter.class);
        return moshiDrawer.adapter(type).toJson(filters);
    }

    public Set<DrawerTagFilter> decodeDrawerTagFilter(@Nullable String json) {
        var type = Types.newParameterizedType(Set.class, DrawerTagFilter.class);
        JsonAdapter<Set<DrawerTagFilter>> adapter = moshiDrawer.adapter(type);
        if (json == null) {
            return Collections.emptySet();
        } else {
            try {
                return adapter.fromJson(json);
            } catch (IOException e) {
                Log.e(TAG, "Unable to decode date added filters", e);
                return Collections.emptySet();
            }
        }
    }
}
