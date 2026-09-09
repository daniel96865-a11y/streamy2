package app.streamy2;

import android.text.Html;
import kotlin.text.Typography;

/* loaded from: classes.dex */
public final class Text {
    public static String clean(String str) {
        if (str == null || str.isEmpty()) {
            return "";
        }
        if (str.indexOf(38) >= 0) {
            try {
                str = Html.fromHtml(str, 0).toString();
            } catch (Exception unused) {
            }
        }
        return str.replace(Typography.nbsp, ' ').trim();
    }

    private Text() {
    }
}
