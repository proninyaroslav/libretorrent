package org.proninyaroslav.libretorrent;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;

import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.fragments.FragmentCallback;
import org.proninyaroslav.libretorrent.fragments.FeedFragment;

public class FeedActivity extends AppCompatActivity implements FragmentCallback
{
    @SuppressWarnings("unused")
    private static final String TAG = FeedActivity.class.getSimpleName();

    public static final String ACTION_ADD_CHANNEL_SHORTCUT = "org.proninyaroslav.libretorrent.ADD_CHANNEL_SHORTCUT";

    FeedFragment feedFragment;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setTheme(Utils.getAppTheme(getApplicationContext()));
        setContentView(R.layout.activity_feed);
        Utils.showColoredStatusBar_KitKat(this);

        feedFragment = (FeedFragment)getFragmentManager()
                .findFragmentById(R.id.feed_fragmentContainer);
    }

    @Override
    public void fragmentFinished(Intent intent, ResultCode code)
    {
        switch (code) {
            case BACK:
                if (feedFragment != null)
                    feedFragment.resetCurOpenFeed();
                break;
            case OK:
            case CANCEL:
                finish();
                break;
        }
    }

    @Override
    public void onBackPressed()
    {
        feedFragment.onBackPressed();
    }
}
