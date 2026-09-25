package app.streamy2;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/* loaded from: classes.dex */
public final class AdBlock {
    private static final WebResourceResponse EMPTY = new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
    private int blocked;
    private final Set<String> hosts = new HashSet();

    public AdBlock(Context context) {
        try {
            InputStream open = context.getAssets().open("adblock.txt");
            try {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(open));
                while (true) {
                    try {
                        String readLine = bufferedReader.readLine();
                        if (readLine == null) {
                            break;
                        }
                        String lowerCase = readLine.trim().toLowerCase(Locale.US);
                        if (!lowerCase.isEmpty() && !lowerCase.startsWith("#")) {
                            this.hosts.add(lowerCase);
                        }
                    } finally {
                    }
                }
                bufferedReader.close();
                if (open != null) {
                    open.close();
                }
            } finally {
            }
        } catch (Exception unused) {
            Quiet.ignored("AdBlock", unused);
        }
    }

    public int blockedCount() {
        return this.blocked;
    }

    public WebResourceResponse intercept(String str) {
        String hostOf;
        if (str == null) {
            return null;
        }
        String lowerCase = str.toLowerCase(Locale.US);
        if (lowerCase.startsWith("data:") || lowerCase.startsWith("blob:") || lowerCase.startsWith("about:") || (hostOf = hostOf(str)) == null) {
            return null;
        }
        if (!isBlockedHost(hostOf) && !looksLikeAdPath(lowerCase)) {
            return null;
        }
        this.blocked++;
        return EMPTY;
    }

    private boolean isBlockedHost(String str) {
        while (str != null && !str.isEmpty()) {
            if (this.hosts.contains(str)) {
                return true;
            }
            int indexOf = str.indexOf(46);
            if (indexOf < 0) {
                return false;
            }
            str = str.substring(indexOf + 1);
        }
        return false;
    }

    private static boolean looksLikeAdPath(String str) {
        return str.contains("/ads/") || str.contains("/ad/") || str.contains("/advert") || str.contains("popunder") || str.contains("popads") || str.contains("/banner") || str.contains("prebid") || str.contains("/vast") || str.contains("adsystem") || str.contains("doubleclick") || str.contains("googlesyndication") || str.contains("pagead") || str.contains("adservice");
    }

    private static String hostOf(String str) {
        try {
            String host = Uri.parse(str).getHost();
            if (host == null) {
                return null;
            }
            return host.toLowerCase(Locale.US);
        } catch (Exception unused) {
            return null;
        }
    }
}
