/*
 * Copyright (C) 2016-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
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

package org.proninyaroslav.libretorrent.ui.addtorrent;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsCompat;
import androidx.databinding.Observable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.PreferenceManager;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import org.proninyaroslav.libretorrent.BR;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.core.exception.DecodeException;
import org.proninyaroslav.libretorrent.core.exception.FetchLinkException;
import org.proninyaroslav.libretorrent.core.exception.FreeSpaceException;
import org.proninyaroslav.libretorrent.core.exception.NoFilesSelectedException;
import org.proninyaroslav.libretorrent.core.exception.TorrentAlreadyExistsException;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.core.utils.WindowInsetsSide;
import org.proninyaroslav.libretorrent.databinding.FragmentAddTorrentBinding;

import java.io.FileNotFoundException;
import java.io.IOException;

import io.reactivex.disposables.CompositeDisposable;

/*
 * The dialog for adding torrent. The parent window.
 */

public class AddTorrentFragment extends Fragment {
    private static final String TAG = AddTorrentFragment.class.getSimpleName();

    private AppCompatActivity activity;
    private Uri inputUri;
    private FragmentAddTorrentBinding binding;
    private AddTorrentViewModel viewModel;
    private final CompositeDisposable disposable = new CompositeDisposable();
    private SharedPreferences localPref;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof AppCompatActivity a) {
            activity = a;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAddTorrentBinding.inflate(inflater, container, false);
        Utils.applyWindowInsets(binding.viewPager, WindowInsetsSide.BOTTOM, WindowInsetsCompat.Type.ime());
        Utils.applyWindowInsets(binding.bottomBar, WindowInsetsSide.ALL, WindowInsetsCompat.Type.ime());

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (activity == null) {
            activity = (AppCompatActivity) requireActivity();
        }

        var args = AddTorrentFragmentArgs.fromBundle(getArguments());
        inputUri = args.getUri();

        var provider = new ViewModelProvider(this);
        viewModel = provider.get(AddTorrentViewModel.class);
        localPref = PreferenceManager.getDefaultSharedPreferences(activity);

        fillMutableParams();
        initLayout();
        observeDecodeState();
    }

    private void fillMutableParams() {
        viewModel.mutableParams.setSequentialDownload(
                localPref.getBoolean(
                        getString(R.string.add_torrent_sequential_download),
                        false
                )
        );
        viewModel.mutableParams.setStartAfterAdd(
                localPref.getBoolean(
                        getString(R.string.add_torrent_start_after_add),
                        true
                )
        );
        viewModel.mutableParams.setIgnoreFreeSpace(
                localPref.getBoolean(
                        getString(R.string.add_torrent_ignore_free_space),
                        false
                )
        );
        viewModel.mutableParams.setFirstLastPiecePriority(
                localPref.getBoolean(
                        getString(R.string.add_torrent_download_first_last_pieces),
                        false
                )
        );
    }

    @Override
    public void onStop() {
        super.onStop();

        binding.viewPager.unregisterOnPageChangeCallback(viewPagerCallback);

        unsubscribeParamsChanged();
        disposable.clear();
    }

    @Override
    public void onStart() {
        super.onStart();

        subscribeParamsChanged();

        binding.viewPager.registerOnPageChangeCallback(viewPagerCallback);
    }

    private void subscribeParamsChanged() {
        viewModel.mutableParams.addOnPropertyChangedCallback(onMutableParamsChanged);
    }

    private void unsubscribeParamsChanged() {
        viewModel.mutableParams.removeOnPropertyChangedCallback(onMutableParamsChanged);
    }

    private final Observable.OnPropertyChangedCallback onMutableParamsChanged =
            new Observable.OnPropertyChangedCallback() {
                @Override
                public void onPropertyChanged(Observable sender, int propertyId) {
                    if (propertyId == BR.sequentialDownload) {
                        localPref.edit()
                                .putBoolean(
                                        getString(R.string.add_torrent_sequential_download),
                                        viewModel.mutableParams.isSequentialDownload()
                                )
                                .apply();
                    } else if (propertyId == BR.startAfterAdd) {
                        localPref.edit()
                                .putBoolean(
                                        getString(R.string.add_torrent_start_after_add),
                                        viewModel.mutableParams.isStartAfterAdd()
                                )
                                .apply();
                    } else if (propertyId == BR.ignoreFreeSpace) {
                        localPref.edit()
                                .putBoolean(
                                        getString(R.string.add_torrent_ignore_free_space),
                                        viewModel.mutableParams.isIgnoreFreeSpace()
                                )
                                .apply();
                    } else if (propertyId == BR.firstLastPiecePriority) {
                        localPref.edit()
                                .putBoolean(
                                        getString(R.string.add_torrent_download_first_last_pieces),
                                        viewModel.mutableParams.isFirstLastPiecePriority()
                                )
                                .apply();
                    }
                }
            };

    private void initLayout() {
        binding.appBar.setNavigationOnClickListener((v) ->
                activity.getOnBackPressedDispatcher().onBackPressed());

        AddTorrentPagerAdapter adapter = new AddTorrentPagerAdapter(this);
        binding.viewPager.setAdapter(adapter);
        binding.viewPager.setOffscreenPageLimit(AddTorrentPagerAdapter.NUM_FRAGMENTS);

        binding.infoButton.setOnClickListener((v) ->
                binding.viewPager.setCurrentItem(AddTorrentPagerAdapter.Page.INFO.position));
        binding.filesButton.setOnClickListener((v) ->
                binding.viewPager.setCurrentItem(AddTorrentPagerAdapter.Page.FILES.position));

        binding.pagesToggleButton.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            MaterialButton button = group.findViewById(checkedId);
            button.setIconResource(isChecked ? R.drawable.ic_check_24px : 0);
        });

        binding.addButton.setOnClickListener((v) -> addTorrent());
    }

    private final ViewPager2.OnPageChangeCallback viewPagerCallback = new ViewPager2.OnPageChangeCallback() {
        @Override
        public void onPageSelected(int position) {
            switch (AddTorrentPagerAdapter.Page.fromPosition(position)) {
                case INFO -> binding.pagesToggleButton.check(R.id.info_button);
                case FILES -> binding.pagesToggleButton.check(R.id.files_button);
            }
        }
    };

    private void observeDecodeState() {
        viewModel.getDecodeState().observe(getViewLifecycleOwner(), (state) -> {
            switch (state.status) {
                case UNKNOWN -> {
                    if (inputUri != null) {
                        viewModel.startDecode(inputUri);
                    }
                }
                case DECODE_TORRENT_FILE,
                     FETCHING_HTTP,
                     FETCHING_MAGNET ->
                        onStartDecode(state.status == AddTorrentViewModel.Status.DECODE_TORRENT_FILE);
                case FETCHING_HTTP_COMPLETED,
                     DECODE_TORRENT_COMPLETED,
                     FETCHING_MAGNET_COMPLETED,
                     ERROR -> onStopDecode(state.error);
            }
        });
    }

    private void onStartDecode(boolean isTorrentFile) {
        binding.progress.setVisibility(View.VISIBLE);
        binding.addButton.setEnabled(!isTorrentFile);
    }

    private void onStopDecode(Throwable e) {
        binding.progress.setVisibility(View.GONE);

        if (e != null) {
            handleDecodeException(e);
        } else {
            viewModel.makeFileTree();
            binding.addButton.setEnabled(true);
        }
    }

    private void addTorrent() {
        String name = viewModel.mutableParams.getName();
        if (TextUtils.isEmpty(name)) {
            showErrorDialog(getString(R.string.error_empty_name));
            return;
        }

        try {
            if (viewModel.addTorrent()) {
                activity.getOnBackPressedDispatcher().onBackPressed();
            }
        } catch (Exception e) {
            if (e instanceof TorrentAlreadyExistsException) {
                Toast.makeText(activity, R.string.torrent_exist, Toast.LENGTH_SHORT).show();
                activity.getOnBackPressedDispatcher().onBackPressed();
            } else {
                handleAddException(e);
            }
        }
    }

    private void handleAddException(Throwable e) {
        Log.e(TAG, "Unable to add a torrent", e);

        if (e instanceof NoFilesSelectedException) {
            showErrorDialog(getString(R.string.error_no_files_selected));
        } else if (e instanceof FreeSpaceException) {
            showErrorDialog(getString(R.string.error_free_space));
        } else if (e instanceof FileNotFoundException) {
            showErrorDialog(getString(R.string.error_file_not_found_add_torrent));
        } else if (e instanceof IOException) {
            showErrorDialog(getString(R.string.error_io_add_torrent));
        } else {
            showErrorDialog(getString(R.string.error_add_torrent), e);
        }
    }

    private void showErrorDialog(String message) {
        showErrorDialog(message, false, null);
    }

    private void showErrorDialog(String message, boolean indefinite) {
        showErrorDialog(message, indefinite, null);
    }

    private void showErrorDialog(String message, Throwable e) {
        showErrorDialog(message, false, e);
    }

    private void showErrorDialog(String message, boolean indefinite, Throwable e) {
        if (e == null) {
            Snackbar.make(
                    activity,
                    binding.coordinatorLayout,
                    message,
                    indefinite ? Snackbar.LENGTH_INDEFINITE : Snackbar.LENGTH_LONG
            ).show();
        } else {
            var action = AddTorrentFragmentDirections.actionErrorReportDialog(message);
            action.setException(e);
            NavHostFragment.findNavController(this).navigate(action);
        }
    }

    public void handleDecodeException(@NonNull Throwable e) {
        Log.e(TAG, "Unable to decode a torrent", e);

        if (e instanceof DecodeException) {
            showErrorDialog(getString(R.string.error_decode_torrent), true);
        } else if (e instanceof FetchLinkException) {
            showErrorDialog(getString(R.string.error_fetch_link), true);
        } else if (e instanceof IllegalArgumentException) {
            showErrorDialog(getString(R.string.error_invalid_link_or_path), true);
        } else if (e instanceof IOException) {
            showErrorDialog(getString(R.string.error_io_torrent), e);
        } else if (e instanceof OutOfMemoryError) {
            showErrorDialog(getString(R.string.file_is_too_large_error), e);
        }
    }
}
