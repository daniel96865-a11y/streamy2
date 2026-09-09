package app.streamy2;

/* loaded from: classes.dex */
public final class Theme {
    public static final Accent[] ALL = {new Accent("blue", "Blau", -10772993, -16378840), new Accent("sky", "Himmel", -8468253, -15981520), new Accent("teal", "Teal", -13709414, -16505830), new Accent("violet", "Violett", -7635713, -15330774), new Accent("rose", "Rose", -809803, -12970976), new Accent("amber", "Amber", -1919638, -14017528)};

    public static final class Accent {
        public final int color;
        public final String id;
        public final String label;
        public final int onColor;

        public Accent(String str, String str2, int i, int i2) {
            this.id = str;
            this.label = str2;
            this.color = i;
            this.onColor = i2;
        }
    }

    public static Accent get(String str) {
        if (str != null) {
            for (Accent accent : ALL) {
                if (accent.id.equals(str)) {
                    return accent;
                }
            }
        }
        return ALL[0];
    }

    private Theme() {
    }
}
