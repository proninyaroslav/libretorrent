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
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;

import androidx.annotation.NonNull;

import org.proninyaroslav.libretorrent.core.system.SystemFacade;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING;

public class FakeSystemFacade implements SystemFacade
{
    public Context appContext;
    public boolean isRoaming;
    public boolean isMetered;
    public int activeNetworkType = ConnectivityManager.TYPE_MOBILE;
    public NetworkInfo.DetailedState connectionState = NetworkInfo.DetailedState.CONNECTED;

    public FakeSystemFacade(@NonNull Context appContext)
    {
        this.appContext = appContext;
    }

    @Override
    public NetworkInfo getActiveNetworkInfo()
    {
        NetworkInfo info;

        try {
            Class<?> netInfoClass = NetworkInfo.class;
            Constructor<?> newInfoConstructor = netInfoClass
                    .getConstructor(int.class, int.class, String.class, String.class);
            Method setDetailedState = netInfoClass
                    .getMethod("setDetailedState", NetworkInfo.DetailedState.class, String.class, String.class);
            Method setRoaming = netInfoClass.getMethod("setRoaming", boolean.class);

            int activeNetworkType = (isMetered ? ConnectivityManager.TYPE_MOBILE : ConnectivityManager.TYPE_WIFI);
            info = (NetworkInfo)newInfoConstructor.newInstance(activeNetworkType, 0, null, null);
            setDetailedState.invoke(info, connectionState, null, null);
            setRoaming.invoke(info, isRoaming);

        } catch (Exception e) {
            return null;
        }

        return info;
    }

    @TargetApi(23)
    @Override
    public NetworkCapabilities getNetworkCapabilities()
    {
        NetworkCapabilities caps;
        try {
            Class<?> netCapsClass = NetworkCapabilities.class;
            Constructor<?> netCapsConstructor = netCapsClass.getConstructor();
            Method addCapability = netCapsClass.getMethod("addCapability", int.class);
            Method removeCapability = netCapsClass.getMethod("removeCapability", int.class);

            caps = (NetworkCapabilities)netCapsConstructor.newInstance();
            if (isMetered)
                removeCapability.invoke(caps, NET_CAPABILITY_NOT_METERED);
            else
                addCapability.invoke(caps, NET_CAPABILITY_NOT_METERED);

            if (isRoaming)
                removeCapability.invoke(caps, NET_CAPABILITY_NOT_ROAMING);
            else
                addCapability.invoke(caps, NET_CAPABILITY_NOT_ROAMING);

        } catch (Exception e) {
            return null;
        }

        return caps;
    }

    @Override
    public boolean isActiveNetworkMetered()
    {
        return isMetered;
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
