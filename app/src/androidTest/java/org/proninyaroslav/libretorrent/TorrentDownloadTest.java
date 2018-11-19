package org.proninyaroslav.libretorrent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.libtorrent4j.AddTorrentParams;
import org.libtorrent4j.Priority;
import org.proninyaroslav.libretorrent.core.TorrentDownload;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Random;

import androidx.test.ext.junit.runners.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class TorrentDownloadTest
{
    @Test
    public void getFileIndicesBep53_isCorrect()
    {
        String baseMagnet = "magnet:?xt=urn:btih:cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd&so=";
        final int n = 100;
        final int indicesLen = 100;
        Random rand = new Random();

        for (int i = 0; i < n; i++) {
            Priority[] expectedPriorities = rand
                            .ints(indicesLen, Priority.IGNORE.swig(), Priority.TWO.swig())
                            .mapToObj(Priority::fromSwig)
                            .toArray(Priority[]::new);

            String indicesStr = TorrentDownload.getFileIndicesBep53(expectedPriorities);
            AddTorrentParams params = null;
            try {
                params = AddTorrentParams.parseMagnetUri(baseMagnet + indicesStr);

            } catch (IllegalArgumentException e) {
                fail(e.getMessage());
            }

            Priority[] actualPriorities = params.filePriorities();
            boolean equal = true;
            for (int j = 0; j < actualPriorities.length; j++) {
                if (expectedPriorities[j].swig() != actualPriorities[j].swig()) {
                    equal = false;
                    break;
                }
            }

            assertFalse("expected: " + Arrays.toString(expectedPriorities) + "\n" +
                    "actual: " + "[" + indicesStr + "]; " + Arrays.toString(actualPriorities), equal);
        }
    }
}
