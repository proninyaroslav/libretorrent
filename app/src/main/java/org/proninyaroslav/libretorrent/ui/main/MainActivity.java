/*
 * Copyright (C) 2016-2021 Yaroslav Pronin <proninyaroslav@mail.ru>
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

import android.app.Dialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.format.Formatter;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.h6ah4i.android.widget.advrecyclerview.animator.GeneralItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.animator.RefactoredDefaultItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.expandable.RecyclerViewExpandableItemManager;

import net.cachapa.expandablelayout.ExpandableLayout;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.filter.TorrentFilterCollection;
import org.proninyaroslav.libretorrent.core.model.TorrentInfoProvider;
import org.proninyaroslav.libretorrent.core.model.data.SessionStats;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.receiver.NotificationReceiver;
import org.proninyaroslav.libretorrent.ui.BaseAlertDialog;
import org.proninyaroslav.libretorrent.ui.FragmentCallback;
import org.proninyaroslav.libretorrent.ui.PermissionDeniedDialog;
import org.proninyaroslav.libretorrent.ui.PermissionManager;
import org.proninyaroslav.libretorrent.ui.addtag.AddTagActivity;
import org.proninyaroslav.libretorrent.ui.customviews.ExpansionHeader;
import org.proninyaroslav.libretorrent.ui.detailtorrent.BlankFragment;
import org.proninyaroslav.libretorrent.ui.detailtorrent.DetailTorrentActivity;
import org.proninyaroslav.libretorrent.ui.detailtorrent.DetailTorrentFragment;
import org.proninyaroslav.libretorrent.ui.feeds.FeedActivity;
import org.proninyaroslav.libretorrent.ui.log.LogActivity;
import org.proninyaroslav.libretorrent.ui.main.drawer.AbstractTagItem;
import org.proninyaroslav.libretorrent.ui.main.drawer.DrawerExpandableAdapter;
import org.proninyaroslav.libretorrent.ui.main.drawer.DrawerGroup;
import org.proninyaroslav.libretorrent.ui.main.drawer.DrawerGroupItem;
import org.proninyaroslav.libretorrent.ui.main.drawer.EmptyTagItem;
import org.proninyaroslav.libretorrent.ui.main.drawer.NoTagsItem;
import org.proninyaroslav.libretorrent.ui.main.drawer.TagItem;
import org.proninyaroslav.libretorrent.ui.main.drawer.TagsAdapter;
import org.proninyaroslav.libretorrent.ui.settings.SettingsActivity;

import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity implements FragmentCallback {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String TAG_ABOUT_DIALOG = "about_dialog";
    private static final String TAG_PERM_DENIED_DIALOG = "perm_denied_dialog";

    public static final String ACTION_ADD_TORRENT_SHORTCUT = "org.proninyaroslav.libretorrent.ADD_TORRENT_SHORTCUT";

    /* Android data binding doesn't work with layout aliases */
    private MainFragment mainFragment;
    private Toolbar toolbar;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ActionBarDrawerToggle toggle;
    private RecyclerView drawerItemsList;
    private LinearLayoutManager layoutManager;
    private DrawerExpandableAdapter drawerAdapter;
    private RecyclerView.Adapter wrappedDrawerAdapter;
    private RecyclerViewExpandableItemManager drawerItemManager;
    private SearchView searchView;
    private TextView sessionDhtNodesStat, sessionDownloadStat,
            sessionUploadStat, sessionListenPortStat;
    private ExpansionHeader tagsGroupHeader;
    private ExpandableLayout tagsExpandable;
    private MaterialButton addTagButton;
    private RecyclerView tagsList;
    private TagsAdapter tagsAdapter;

    private MainViewModel viewModel;
    private MsgMainViewModel msgViewModel;
    private CompositeDisposable disposables = new CompositeDisposable();
    private BaseAlertDialog.SharedViewModel dialogViewModel;
    private BaseAlertDialog aboutDialog;
    private TorrentInfoProvider infoProvider;
    private PermissionDeniedDialog permDeniedDialog;
    private PermissionManager permissionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(Utils.getAppTheme(getApplicationContext()));
        super.onCreate(savedInstanceState);

        if (getIntent().getAction() != null &&
                getIntent().getAction().equals(NotificationReceiver.NOTIFY_ACTION_SHUTDOWN_APP)) {
            finish();
            return;
        }

        infoProvider = TorrentInfoProvider.getInstance(getApplicationContext());
        ViewModelProvider provider = new ViewModelProvider(this);
        viewModel = provider.get(MainViewModel.class);
        msgViewModel = provider.get(MsgMainViewModel.class);
        dialogViewModel = provider.get(BaseAlertDialog.SharedViewModel.class);

        FragmentManager fm = getSupportFragmentManager();
        permissionManager = new PermissionManager(this, new PermissionManager.Callback() {
            @Override
            public void onStorageResult(boolean isGranted, boolean shouldRequestStoragePermission) {
                if (!isGranted && shouldRequestStoragePermission) {
                    if (fm.findFragmentByTag(TAG_PERM_DENIED_DIALOG) == null) {
                        permDeniedDialog = PermissionDeniedDialog.newInstance();
                        FragmentTransaction ft = fm.beginTransaction();
                        ft.add(permDeniedDialog, TAG_PERM_DENIED_DIALOG);
                        ft.commitAllowingStateLoss();
                    }
                }
            }

            @Override
            public void onNotificationResult(boolean isGranted, boolean shouldRequestNotificationPermission) {
                permissionManager.setDoNotAskNotifications(!isGranted);
                if (isGranted) {
                    viewModel.restartForegroundNotification();
                }
            }
        });
        aboutDialog = (BaseAlertDialog) fm.findFragmentByTag(TAG_ABOUT_DIALOG);
        permDeniedDialog = (PermissionDeniedDialog) fm.findFragmentByTag(TAG_PERM_DENIED_DIALOG);

        if (!permissionManager.checkPermissions() && permDeniedDialog == null) {
            permissionManager.requestPermissions();
        }
        Utils.showManageAllFilesWarningDialog(getApplicationContext(), getSupportFragmentManager());

        setContentView(R.layout.activity_main);

        mainFragment = (MainFragment) getSupportFragmentManager()
                .findFragmentById(R.id.main_fragmentContainer);

        cleanGarbageFragments();
        initLayout();
    }

    private void initLayout() {
        showBlankFragment();

        toolbar = findViewById(R.id.toolbar);
        navigationView = findViewById(R.id.navigation_view);
        drawerLayout = findViewById(R.id.drawer_layout);
        drawerItemsList = findViewById(R.id.drawer_items_list);
        layoutManager = new LinearLayoutManager(this) {
            @Override
            public boolean canScrollVertically() {
                /* Disable scroll, because RecyclerView is wrapped in ScrollView */
                return false;
            }
        };
        sessionDhtNodesStat = findViewById(R.id.session_dht_nodes_stat);
        sessionDownloadStat = findViewById(R.id.session_download_stat);
        sessionUploadStat = findViewById(R.id.session_upload_stat);
        sessionListenPortStat = findViewById(R.id.session_listen_port_stat);
        tagsGroupHeader = findViewById(R.id.tags_group_header);
        tagsExpandable = findViewById(R.id.tags_expandable);
        addTagButton = findViewById(R.id.add_tag_button);
        tagsList = findViewById(R.id.tags_list);

        toolbar.setTitle(R.string.app_name);
        toolbar.inflateMenu(R.menu.main);
        setSupportActionBar(toolbar);

        if (drawerLayout != null) {
            toggle = new ActionBarDrawerToggle(this,
                    drawerLayout,
                    toolbar,
                    R.string.open_navigation_drawer,
                    R.string.close_navigation_drawer);
            drawerLayout.addDrawerListener(toggle);
        }
        initDrawer();
        viewModel.resetSearch();
    }

    private void initDrawer() {
        drawerItemManager = new RecyclerViewExpandableItemManager(null);
        drawerItemManager.setDefaultGroupsExpandedState(false);
        drawerItemManager.setOnGroupCollapseListener((groupPosition, fromUser, payload) -> {
            if (fromUser)
                saveGroupExpandState(groupPosition, false);
        });
        drawerItemManager.setOnGroupExpandListener((groupPosition, fromUser, payload) -> {
            if (fromUser)
                saveGroupExpandState(groupPosition, true);
        });
        GeneralItemAnimator animator = new RefactoredDefaultItemAnimator();
        /*
         * Change animations are enabled by default since support-v7-recyclerview v22.
         * Need to disable them when using animation indicator.
         */
        animator.setSupportsChangeAnimations(false);

        List<DrawerGroup> groups = Utils.getNavigationDrawerItems(this,
                PreferenceManager.getDefaultSharedPreferences(this));
        drawerAdapter = new DrawerExpandableAdapter(groups, drawerItemManager, this::onDrawerItemSelected);
        wrappedDrawerAdapter = drawerItemManager.createWrappedAdapter(drawerAdapter);
        onDrawerGroupsCreated();

        drawerItemsList.setLayoutManager(layoutManager);
        drawerItemsList.setAdapter(wrappedDrawerAdapter);
        drawerItemsList.setItemAnimator(animator);
        drawerItemsList.setHasFixedSize(false);

        drawerItemManager.attachRecyclerView(drawerItemsList);

        sessionDhtNodesStat.setText(getString(R.string.session_stats_dht_nodes, 0));
        String downloadUploadFmt = getString(R.string.session_stats_download_upload,
                Formatter.formatFileSize(this, 0),
                Formatter.formatFileSize(this, 0));
        sessionDownloadStat.setText(downloadUploadFmt);
        sessionUploadStat.setText(downloadUploadFmt);
        sessionListenPortStat.setText(getString(R.string.session_stats_listen_port,
                getString(R.string.not_available)));

        tagsList.setLayoutManager(new LinearLayoutManager(this));
        tagsAdapter = new TagsAdapter(tagsClickListener);
        tagsList.setAdapter(tagsAdapter);
        addTagButton.setOnClickListener(
                (v) -> startActivity(new Intent(this, AddTagActivity.class))
        );

        boolean tagsExpanded = PreferenceManager
                .getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.drawer_tags_is_expanded), false);
        tagsGroupHeader.setExpanded(tagsExpanded);
        tagsExpandable.setExpanded(tagsExpanded);
        tagsGroupHeader.setOnClickListener((v) -> {
            tagsExpandable.toggle();
            tagsGroupHeader.toggleExpand();
            PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putBoolean(
                            getString(R.string.drawer_tags_is_expanded),
                            tagsExpandable.isExpanded()
                    )
                    .apply();
        });
    }

    private final TagsAdapter.OnClickListener tagsClickListener = new TagsAdapter.OnClickListener() {
        @Override
        public void onTagSelected(@NonNull AbstractTagItem item) {
            if (item instanceof TagItem) {
                viewModel.setTagFilter(
                        TorrentFilterCollection.tag(((TagItem) item).info),
                        true
                );
            } else if (item instanceof EmptyTagItem) {
                viewModel.setTagFilter(TorrentFilterCollection.all(), true);
            } else if (item instanceof NoTagsItem) {
                viewModel.setTagFilter(TorrentFilterCollection.noTags(), true);
            }

            saveSelectedTag(item);

            if (drawerLayout != null)
                drawerLayout.closeDrawer(GravityCompat.START);
        }

        @Override
        public void onTagMenuClicked(@NonNull AbstractTagItem abstractItem, int menuId) {
            if (!(abstractItem instanceof TagItem)) {
                return;
            }
            TagItem item = ((TagItem) abstractItem);
            if (menuId == R.id.edit_tag_menu) {
                Intent i = new Intent(MainActivity.this, AddTagActivity.class);
                i.putExtra(AddTagActivity.TAG_INIT_INFO, item.info);
                startActivity(i);
            } else if (menuId == R.id.delete_tag_menu) {
                disposables.add(viewModel.deleteTag(item.info)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                () -> {
                                    if (item.isSame(tagsAdapter.getSelectedItem())) {
                                        EmptyTagItem emptyItem = new EmptyTagItem();
                                        saveSelectedTag(emptyItem);
                                        tagsAdapter.setSelectedItem(emptyItem);
                                        viewModel.setTagFilter(TorrentFilterCollection.all(), true);
                                    }
                                },
                                (e) -> {
                                    Log.e(TAG, Log.getStackTraceString(e));
                                    Snackbar.make(
                                            mainFragment.getCoordinatorLayout(),
                                            R.string.tag_deleting_failed,
                                            Snackbar.LENGTH_LONG
                                    ).show();
                                }
                        )
                );
            }
        }
    };

    private void saveSelectedTag(@NonNull AbstractTagItem item) {
        String tagId = null;
        if (item instanceof TagItem) {
            tagId = Long.toString(((TagItem) item).info.id);
        } else if (item instanceof EmptyTagItem) {
            tagId = getString(R.string.tag_empty_item);
        } else if (item instanceof NoTagsItem) {
            tagId = getString(R.string.tag_no_tags_item);
        }

        PreferenceManager
                .getDefaultSharedPreferences(MainActivity.this)
                .edit()
                .putString(
                        getString(R.string.drawer_tags_selected_item),
                        tagId
                )
                .apply();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        if (toggle != null)
            toggle.syncState();
    }

    @Override
    public void onStart() {
        super.onStart();

        subscribeAlertDialog();
        subscribeMsgViewModel();
        subscribeSessionStats();
        subscribeNeedStartEngine();
        subscribeTags();
    }

    @Override
    protected void onStop() {
        super.onStop();

        disposables.clear();
    }

    @Override
    protected void onDestroy() {
        if (viewModel != null)
            viewModel.requestStopEngine();

        super.onDestroy();
    }

    private void subscribeAlertDialog() {
        Disposable d = dialogViewModel.observeEvents()
                .subscribe((event) -> {
                    if (event.dialogTag == null) {
                        return;
                    }

                    if (event.dialogTag.equals(TAG_ABOUT_DIALOG)) {
                        switch (event.type) {
                            case NEGATIVE_BUTTON_CLICKED:
                                openChangelogLink();
                                break;
                            case DIALOG_SHOWN:
                                initAboutDialog();
                                break;
                        }
                    } else if (event.dialogTag.equals(TAG_PERM_DENIED_DIALOG)) {
                        if (event.type != BaseAlertDialog.EventType.DIALOG_SHOWN) {
                            permDeniedDialog.dismiss();
                        }
                        if (event.type == BaseAlertDialog.EventType.NEGATIVE_BUTTON_CLICKED) {
                            permissionManager.requestPermissions();
                        }
                        if (event.type == BaseAlertDialog.EventType.POSITIVE_BUTTON_CLICKED) {
                            permissionManager.setDoNotAskStorage(true);
                        }
                    }
                });
        disposables.add(d);
    }

    private void subscribeMsgViewModel() {
        disposables.add(msgViewModel.observeTorrentDetailsOpened()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::showDetailTorrent));

        disposables.add(msgViewModel.observeTorrentDetailsClosed()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((__) -> showBlankFragment()));

        disposables.add(viewModel.observeTorrentsDeleted()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((id) -> {
                    DetailTorrentFragment f = getCurrentDetailFragment();
                    if (f != null && id.equals(f.getTorrentId()))
                        showBlankFragment();
                }));
    }

    private void subscribeSessionStats() {
        disposables.add(infoProvider.observeSessionStats()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::updateSessionStats));
    }

    private void subscribeNeedStartEngine() {
        disposables.add(viewModel.observeNeedStartEngine()
                .subscribeOn(Schedulers.io())
                .filter((needStart) -> needStart)
                .subscribe((needStart) -> viewModel.startEngine()));
    }

    private void subscribeTags() {
        disposables.add(viewModel.observeTags()
                .subscribeOn(Schedulers.io())
                .flatMapSingle((list) ->
                        Flowable.concat(
                                Flowable.just(new EmptyTagItem()),
                                Flowable.just(new NoTagsItem()),
                                Flowable.fromIterable(list).map(TagItem::new)
                        ).toList()
                )
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((items) -> {
                    if (tagsAdapter.getItemCount() == 0) {
                        setInitSelection(items);
                    }
                    tagsAdapter.submitList(items);
                })
        );
    }

    private void setInitSelection(List<AbstractTagItem> items) {
        String selectedTagIdStr = PreferenceManager
                .getDefaultSharedPreferences(this)
                .getString(
                        getString(R.string.drawer_tags_selected_item),
                        getString(R.string.tag_empty_item)
                );
        if (selectedTagIdStr.equals(getString(R.string.tag_empty_item))) {
            tagsAdapter.setSelectedItem(new EmptyTagItem());
            viewModel.setTagFilter(TorrentFilterCollection.all(), true);
        } else if (selectedTagIdStr.equals(getString(R.string.tag_no_tags_item))) {
            tagsAdapter.setSelectedItem(new NoTagsItem());
            viewModel.setTagFilter(TorrentFilterCollection.noTags(), true);
        } else {
            long selectedTagId;
            try {
                selectedTagId = Long.parseLong(selectedTagIdStr);
            } catch (NumberFormatException e) {
                Log.e(TAG, "Unable to parse tag id: " + Log.getStackTraceString(e));
                tagsAdapter.setSelectedItem(new EmptyTagItem());
                viewModel.setTagFilter(TorrentFilterCollection.all(), true);
                return;
            }
            disposables.add(Observable.fromIterable(items)
                    .subscribeOn(Schedulers.computation())
                    .filter((item) -> item instanceof TagItem &&
                            ((TagItem) item).info.id == selectedTagId)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((item) -> {
                        tagsAdapter.setSelectedItem(item);
                        viewModel.setTagFilter(
                                TorrentFilterCollection.tag(((TagItem) item).info),
                                true
                        );
                    })
            );
        }
    }

    private void updateSessionStats(SessionStats stats) {
        long dhtNodes = 0;
        long totalDownload = 0;
        long totalUpload = 0;
        long downloadSpeed = 0;
        long uploadSpeed = 0;
        int listenPort = -1;

        if (stats != null) {
            dhtNodes = stats.dhtNodes;
            totalDownload = stats.totalDownload;
            totalUpload = stats.totalUpload;
            downloadSpeed = stats.downloadSpeed;
            uploadSpeed = stats.uploadSpeed;
            listenPort = stats.listenPort;
        }

        sessionDhtNodesStat.setText(getString(R.string.session_stats_dht_nodes, dhtNodes));
        sessionDownloadStat.setText(getString(R.string.session_stats_download_upload,
                Formatter.formatFileSize(this, totalDownload),
                Formatter.formatFileSize(this, downloadSpeed)));
        sessionUploadStat.setText(getString(R.string.session_stats_download_upload,
                Formatter.formatFileSize(this, totalUpload),
                Formatter.formatFileSize(this, uploadSpeed)));
        sessionListenPortStat.setText(getString(R.string.session_stats_listen_port,
                listenPort <= 0 ?
                        getString(R.string.not_available) :
                        Integer.toString(listenPort)));
    }

    private void saveGroupExpandState(int groupPosition, boolean expanded) {
        DrawerGroup group = drawerAdapter.getGroup(groupPosition);
        if (group == null)
            return;

        Resources res = getResources();
        String prefKey = null;
        if (group.id == res.getInteger(R.integer.drawer_status_id))
            prefKey = getString(R.string.drawer_status_is_expanded);

        else if (group.id == res.getInteger(R.integer.drawer_sorting_id))
            prefKey = getString(R.string.drawer_sorting_is_expanded);

        else if (group.id == res.getInteger(R.integer.drawer_date_added_id))
            prefKey = getString(R.string.drawer_time_is_expanded);

        if (prefKey != null)
            PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putBoolean(prefKey, expanded)
                    .apply();
    }

    private void onDrawerGroupsCreated() {
        for (int pos = 0; pos < drawerAdapter.getGroupCount(); pos++) {
            DrawerGroup group = drawerAdapter.getGroup(pos);
            if (group == null)
                return;

            Resources res = getResources();
            if (group.id == res.getInteger(R.integer.drawer_status_id)) {
                viewModel.setStatusFilter(
                        Utils.getDrawerGroupStatusFilter(this, group.getSelectedItemId()), false);

            } else if (group.id == res.getInteger(R.integer.drawer_sorting_id)) {
                viewModel.setSort(Utils.getDrawerGroupItemSorting(this, group.getSelectedItemId()), false);
            } else if (group.id == res.getInteger(R.integer.drawer_date_added_id)) {
                viewModel.setDateAddedFilter(Utils.getDrawerGroupDateAddedFilter(this, group.getSelectedItemId()), false);
            }

            applyExpandState(group, pos);
        }
    }

    private void applyExpandState(DrawerGroup group, int pos) {
        if (group.getDefaultExpandState())
            drawerItemManager.expandGroup(pos);
        else
            drawerItemManager.collapseGroup(pos);
    }

    private void onDrawerItemSelected(DrawerGroup group, DrawerGroupItem item) {
        Resources res = getResources();
        String prefKey = null;
        if (group.id == res.getInteger(R.integer.drawer_status_id)) {
            prefKey = getString(R.string.drawer_status_selected_item);
            viewModel.setStatusFilter(Utils.getDrawerGroupStatusFilter(this, item.id), true);

        } else if (group.id == res.getInteger(R.integer.drawer_sorting_id)) {
            prefKey = getString(R.string.drawer_sorting_selected_item);
            viewModel.setSort(Utils.getDrawerGroupItemSorting(this, item.id), true);

        } else if (group.id == res.getInteger(R.integer.drawer_date_added_id)) {
            prefKey = getString(R.string.drawer_time_selected_item);
            viewModel.setDateAddedFilter(Utils.getDrawerGroupDateAddedFilter(this, item.id), true);
        }

        if (prefKey != null)
            saveSelectionState(prefKey, item);

        if (drawerLayout != null)
            drawerLayout.closeDrawer(GravityCompat.START);
    }

    private void saveSelectionState(String prefKey, DrawerGroupItem item) {
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putLong(prefKey, item.id)
                .apply();
    }

    private void cleanGarbageFragments() {
        /* Clean detail and blank fragments after rotate for tablets */
        if (Utils.isLargeScreenDevice(this)) {
            FragmentManager fm = getSupportFragmentManager();
            List<Fragment> fragments = fm.getFragments();
            FragmentTransaction ft = fm.beginTransaction();
            for (Fragment f : fragments)
                if (f instanceof DetailTorrentFragment || f instanceof BlankFragment)
                    ft.remove(f);
            ft.commitAllowingStateLoss();
        }
    }

    private void showDetailTorrent(String id) {
        if (Utils.isTwoPane(this)) {
            FragmentManager fm = getSupportFragmentManager();
            DetailTorrentFragment detail = DetailTorrentFragment.newInstance(id);
            Fragment fragment = fm.findFragmentById(R.id.detail_torrent_fragmentContainer);

            if (fragment instanceof DetailTorrentFragment) {
                String oldId = ((DetailTorrentFragment) fragment).getTorrentId();
                if (id.equals(oldId))
                    return;
            }
            fm.beginTransaction()
                    .replace(R.id.detail_torrent_fragmentContainer, detail)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .commit();

        } else {
            Intent i = new Intent(this, DetailTorrentActivity.class);
            i.putExtra(DetailTorrentActivity.TAG_TORRENT_ID, id);
            startActivity(i);
        }
    }

    private void showBlankFragment() {
        if (Utils.isTwoPane(this)) {
            FragmentManager fm = getSupportFragmentManager();
            BlankFragment blank = BlankFragment.newInstance(getString(R.string.select_or_add_torrent));
            fm.beginTransaction()
                    .replace(R.id.detail_torrent_fragmentContainer, blank)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
                    .commitAllowingStateLoss();
        }
    }

    public DetailTorrentFragment getCurrentDetailFragment() {
        if (!Utils.isTwoPane(this))
            return null;

        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentById(R.id.detail_torrent_fragmentContainer);

        return (fragment instanceof DetailTorrentFragment ? (DetailTorrentFragment) fragment : null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        toolbar.inflateMenu(R.menu.main);
        searchView = (SearchView) toolbar.getMenu().findItem(R.id.search).getActionView();
        initSearch();
        toolbar.setOnMenuItemClickListener(this::onOptionsItemSelected);

        return true;
    }

    private void initSearch() {
        searchView.setMaxWidth(Integer.MAX_VALUE);
        searchView.setOnCloseListener(() -> {
            viewModel.resetSearch();

            return false;
        });
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                viewModel.setSearchQuery(query);
                /* Submit the search will hide the keyboard */
                searchView.clearFocus();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                viewModel.setSearchQuery(newText);

                return true;
            }
        });
        searchView.setQueryHint(getString(R.string.search));
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        /* Assumes current activity is the searchable activity */
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.feed_menu) {
            startActivity(new Intent(this, FeedActivity.class));
        } else if (itemId == R.id.settings_menu) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (itemId == R.id.about_menu) {
            showAboutDialog();
        } else if (itemId == R.id.shutdown_app_menu) {
            closeOptionsMenu();
            viewModel.stopEngine();
            finish();
        } else if (itemId == R.id.pause_all_menu) {
            viewModel.pauseAll();
        } else if (itemId == R.id.resume_all_menu) {
            viewModel.resumeAll();
        } else if (itemId == R.id.log_menu) {
            showLog();
        }

        return true;
    }

    private void showAboutDialog() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentByTag(TAG_ABOUT_DIALOG) == null) {
            aboutDialog = BaseAlertDialog.newInstance(
                    getString(R.string.about_title),
                    null,
                    R.layout.dialog_about,
                    getString(R.string.ok),
                    getString(R.string.about_changelog),
                    null,
                    true);
            aboutDialog.show(fm, TAG_ABOUT_DIALOG);
        }
    }

    private void initAboutDialog() {
        if (aboutDialog == null)
            return;

        Dialog dialog = aboutDialog.getDialog();
        if (dialog != null) {
            TextView versionTextView = dialog.findViewById(R.id.about_version);
            TextView descriptionTextView = dialog.findViewById(R.id.about_description);
            String versionName = SystemFacadeHelper.getSystemFacade(getApplicationContext())
                    .getAppVersionName();
            if (versionName != null)
                versionTextView.setText(versionName);
            descriptionTextView.setText(
                    Html.fromHtml(getString(R.string.about_description), Html.FROM_HTML_MODE_LEGACY)
            );
            descriptionTextView.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    private void openChangelogLink() {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(getString(R.string.about_changelog_link)));
        startActivity(i);
    }

    private void showLog() {
        startActivity(new Intent(this, LogActivity.class));
    }

    @Override
    public void onFragmentFinished(@NonNull Fragment f, Intent intent,
                                   @NonNull ResultCode code) {
        if (f instanceof DetailTorrentFragment && Utils.isTwoPane(this))
            msgViewModel.torrentDetailsClosed();
    }
}
