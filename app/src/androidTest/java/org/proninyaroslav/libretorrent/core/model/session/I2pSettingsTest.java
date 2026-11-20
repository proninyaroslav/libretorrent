package org.proninyaroslav.libretorrent.core.model.session;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.libtorrent4j.SettingsPack;
import org.libtorrent4j.swig.settings_pack;
import org.proninyaroslav.libretorrent.core.settings.SessionSettings;

import java.lang.reflect.Method;

@RunWith(AndroidJUnit4.class)
public class I2pSettingsTest {
    @Test
    public void disablingAndReenablingClearsAndRestoresSamHost() throws Exception {
        TorrentSessionImpl session = new TorrentSessionImpl(null, null, null);
        Method apply = TorrentSessionImpl.class.getDeclaredMethod("applyI2p", SessionSettings.class, SettingsPack.class);
        apply.setAccessible(true);
        SettingsPack pack = new SettingsPack();
        SessionSettings settings = new SessionSettings();
        settings.i2pEnabled = true;
        settings.i2pHostname = "127.0.0.1";
        int hostname = settings_pack.string_types.i2p_hostname.swigValue();
        apply.invoke(session, settings, pack);
        assertEquals("127.0.0.1", pack.swig().get_str(hostname));
        settings.i2pEnabled = false;
        apply.invoke(session, settings, pack);
        assertEquals("", pack.swig().get_str(hostname));
        settings.i2pEnabled = true;
        apply.invoke(session, settings, pack);
        assertEquals("127.0.0.1", pack.swig().get_str(hostname));
    }
}
