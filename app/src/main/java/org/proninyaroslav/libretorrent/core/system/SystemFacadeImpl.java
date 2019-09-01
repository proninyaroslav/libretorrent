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

package org.proninyaroslav.libretorrent.core.system;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;

import androidx.annotation.NonNull;
import androidx.core.net.ConnectivityManagerCompat;

public class SystemFacadeImpl implements SystemFacade
{
    private Context appContext;

    public SystemFacadeImpl(@NonNull Context appContext)
    {
        this.appContext = appContext;
    }

    @Override
    public NetworkInfo getActiveNetworkInfo()
    {
        ConnectivityManager cm = (ConnectivityManager)appContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        return cm.getActiveNetworkInfo();
    }

    @TargetApi(23)
    @Override
    public NetworkCapabilities getNetworkCapabilities()
    {
        ConnectivityManager cm = (ConnectivityManager)appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network network = cm.getActiveNetwork();
        if (network == null)
            return null;

        return cm.getNetworkCapabilities(network);
    }

    @Override
    public boolean isActiveNetworkMetered()
    {
        ConnectivityManager cm = (ConnectivityManager)appContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        return ConnectivityManagerCompat.isActiveNetworkMetered(cm);
    }

    @Override
    public String getAppVersionName()
    {
        try {
            PackageInfo info = appContext.getPackageManager().getPackageInfo(appContext.getPackageName(), 0);

            return info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            /* Ignore */
        }

        return null;
    }
}
