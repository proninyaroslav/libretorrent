/*
 * Copyright (C) 2016, 2017, 2019 Yaroslav Pronin <proninyaroslav@mail.ru>
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
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.format.Formatter;
import android.text.method.LinkMovementMethod;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;
import com.h6ah4i.android.widget.advrecyclerview.animator.GeneralItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.animator.RefactoredDefaultItemAnimator;
import com.h6ah4i.android.widget.advrecyclerview.expandable.RecyclerViewExpandableItemManager;

import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.model.TorrentInfoProvider;
import org.proninyaroslav.libretorrent.core.model.data.SessionStats;
import org.proninyaroslav.libretorrent.core.system.SystemFacadeHelper;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.receiver.NotificationReceiver;
import org.proninyaroslav.libretorrent.ui.BaseAlertDialog;
import org.proninyaroslav.libretorrent.ui.FragmentCallback;
import org.proninyaroslav.libretorrent.ui.RequestPermissions;
import org.proninyaroslav.libretorrent.ui.detailtorrent.BlankFragment;
import org.proninyaroslav.libretorrent.ui.detailtorrent.DetailTorrentActivity;
import org.proninyaroslav.libretorrent.ui.detailtorrent.DetailTorrentFragment;
import org.proninyaroslav.libretorrent.ui.feeds.FeedActivity;
import org.proninyaroslav.libretorrent.ui.log.LogActivity;
import org.proninyaroslav.libretorrent.ui.main.drawer.DrawerExpandableAdapter;
import org.proninyaroslav.libretorrent.ui.main.drawer.DrawerGroup;
import org.proninyaroslav.libretorrent.ui.main.drawer.DrawerGroupItem;
import org.proninyaroslav.libretorrent.ui.settings.SettingsActivity;

import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity implements FragmentCallback
{
    @SuppressWarnings("unused")
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String TAG_PERM_DIALOG_IS_SHOW = "perm_dialog_is_show";
    private static final String TAG_ABOUT_DIALOG = "about_dialog";

    public static final String ACTION_ADD_TORRENT_SHORTCUT = "org.proninyaroslav.libretorrent.ADD_TORRENT_SHORTCUT";

    /* Android data binding doesn't work with layout aliases */
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

    private MainViewModel viewModel;
    private MsgMainViewModel msgViewModel;
    private CompositeDisposable disposables = new CompositeDisposable();
    private BaseAlertDialog.SharedViewModel dialogViewModel;
    private BaseAlertDialog aboutDialog;
    private boolean permDialogIsShow = false;
    private TorrentInfoProvider infoProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
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
        aboutDialog = (BaseAlertDialog)getSupportFragmentManager().findFragmentByTag(TAG_ABOUT_DIALOG);

        if (savedInstanceState != null)
            permDialogIsShow = savedInstanceState.getBoolean(TAG_PERM_DIALOG_IS_SHOW);

        if (!Utils.checkStoragePermission(getApplicationContext()) && !permDialogIsShow) {
            permDialogIsShow = true;
            startActivity(new Intent(this, RequestPermissions.class));
        }

        setContentView(R.layout.activity_main);

        cleanGarbageFragments();
        initLayout();
    }

    private void initLayout()
    {
        showBlankFragment();

        toolbar = findViewById(R.id.toolbar);
        navigationView = findViewById(R.id.navigation_view);
        drawerLayout = findViewById(R.id.drawer_layout);
        drawerItemsList = findViewById(R.id.drawer_items_list);
        layoutManager = new LinearLayoutManager(this) {
            @Override
            public boolean canScrollVertically()
            {
                /* Disable scroll, because RecyclerView is wrapped in ScrollView */
                return false;
            }
        };
        sessionDhtNodesStat = findViewById(R.id.session_dht_nodes_stat);
        sessionDownloadStat = findViewById(R.id.session_download_stat);
        sessionUploadStat = findViewById(R.id.session_upload_stat);
        sessionListenPortStat = findViewById(R.id.session_listen_port_stat);

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

    private void initDrawer()
    {
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
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState)
    {
        outState.putBoolean(TAG_PERM_DIALOG_IS_SHOW, permDialogIsShow);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState)
    {
        super.onPostCreate(savedInstanceState);

        if (toggle != null)
            toggle.syncState();
    }

    @Override
    public void onStart()
    {
        super.onStart();

        subscribeAlertDialog();
        subscribeMsgViewModel();
        subscribeSessionStats();
        subscribeNeedStartEngine();
    }

    @Override
    protected void onStop()
    {
        super.onStop();

        disposables.clear();
    }

    @Override
    protected void onDestroy()
    {
        if (viewModel != null)
            viewModel.requestStopEngine();

        super.onDestroy();
    }

    private void subscribeAlertDialog()
    {
        Disposable d = dialogViewModel.observeEvents()
                .subscribe((event) -> {
                    if (event.dialogTag == null || !event.dialogTag.equals(TAG_ABOUT_DIALOG))
                        return;
                    switch (event.type) {
                        case NEGATIVE_BUTTON_CLICKED:
                            openChangelogLink();
                            break;
                        case DIALOG_SHOWN:
                            initAboutDialog();
                            break;
                    }
                });
        disposables.add(d);
    }

    private void subscribeMsgViewModel()
    {
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

    private void subscribeSessionStats()
    {
        disposables.add(infoProvider.observeSessionStats()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::updateSessionStats));
    }

    private void subscribeNeedStartEngine()
    {
        disposables.add(viewModel.observeNeedStartEngine()
                .subscribeOn(Schedulers.io())
                .filter((needStart) -> needStart)
                .subscribe((needStart) -> viewModel.startEngine()));
    }

    private void updateSessionStats(SessionStats stats)
    {
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

    private void saveGroupExpandState(int groupPosition, boolean expanded)
    {
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

    private void onDrawerGroupsCreated()
    {
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

    private void applyExpandState(DrawerGroup group, int pos)
    {
        if (group.getDefaultExpandState())
            drawerItemManager.expandGroup(pos);
        else
            drawerItemManager.collapseGroup(pos);
    }

    private void onDrawerItemSelected(DrawerGroup group, DrawerGroupItem item)
    {
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

    private void saveSelectionState(String prefKey, DrawerGroupItem item)
    {
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putLong(prefKey, item.id)
                .apply();
    }

    private void cleanGarbageFragments()
    {
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

    private void showDetailTorrent(String id)
    {
        if (Utils.isTwoPane(this)) {
            FragmentManager fm = getSupportFragmentManager();
            DetailTorrentFragment detail = DetailTorrentFragment.newInstance(id);
            Fragment fragment = fm.findFragmentById(R.id.detail_torrent_fragmentContainer);

            if (fragment instanceof DetailTorrentFragment) {
                String oldId = ((DetailTorrentFragment)fragment).getTorrentId();
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

    private void showBlankFragment()
    {
        if (Utils.isTwoPane(this)) {
            FragmentManager fm = getSupportFragmentManager();
            BlankFragment blank = BlankFragment.newInstance(getString(R.string.select_or_add_torrent));
            fm.beginTransaction()
                    .replace(R.id.detail_torrent_fragmentContainer, blank)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
                    .commitAllowingStateLoss();
        }
    }

    public DetailTorrentFragment getCurrentDetailFragment()
    {
        if (!Utils.isTwoPane(this))
            return null;

        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentById(R.id.detail_torrent_fragmentContainer);

        return (fragment instanceof DetailTorrentFragment ? (DetailTorrentFragment)fragment : null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        toolbar.inflateMenu(R.menu.main);
        searchView = (SearchView)toolbar.getMenu().findItem(R.id.search).getActionView();
        initSearch();
        toolbar.setOnMenuItemClickListener(this::onOptionsItemSelected);

        return true;
    }

    private void initSearch()
    {
        searchView.setMaxWidth(Integer.MAX_VALUE);
        searchView.setOnCloseListener(() -> {
            viewModel.resetSearch();

            return false;
        });
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener()
        {
            @Override
            public boolean onQueryTextSubmit(String query)
            {
                viewModel.setSearchQuery(query);
                /* Submit the search will hide the keyboard */
                searchView.clearFocus();

                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText)
            {
                viewModel.setSearchQuery(newText);

                return true;
            }
        });
        searchView.setQueryHint(getString(R.string.search));
        SearchManager searchManager = (SearchManager)getSystemService(Context.SEARCH_SERVICE);
        /* Assumes current activity is the searchable activity */
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
            case R.id.feed_menu:
                startActivity(new Intent(this, FeedActivity.class));
                break;
            case R.id.settings_menu:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            case R.id.about_menu:
                showAboutDialog();
                break;
            case R.id.shutdown_app_menu:
                closeOptionsMenu();
                viewModel.stopEngine();
                finish();
                break;
            case R.id.pause_all_menu:
                viewModel.pauseAll();
                break;
            case R.id.resume_all_menu:
                viewModel.resumeAll();
                break;
            case R.id.log_menu:
                showLog();
                break;
        }

        return true;
    }

    private void showAboutDialog()
    {
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

    private void initAboutDialog()
    {
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
            descriptionTextView.setText(Html.fromHtml(getString(R.string.about_description)));
            descriptionTextView.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    private void openChangelogLink()
    {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(getString(R.string.about_changelog_link)));
        startActivity(i);
    }

    private void showLog()
    {
        startActivity(new Intent(this, LogActivity.class));
    }

    @Override
    public void onFragmentFinished(@NonNull Fragment f, Intent intent,
                                   @NonNull ResultCode code)
    {
        if (f instanceof DetailTorrentFragment && Utils.isTwoPane(this))
            msgViewModel.torrentDetailsClosed();
    }
}
