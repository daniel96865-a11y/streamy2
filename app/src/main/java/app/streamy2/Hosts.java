package app.streamy2;

/* loaded from: classes.dex */
final class Hosts {
    Hosts() {
    }

    static String resolve(String str) {
        String extract;
        String extract2;
        if (str != null && !str.isEmpty()) {
            if (str.startsWith("//")) {
                str = "https:" + str;
            }
            String lowerCase = str.toLowerCase();
            if (!lowerCase.contains("youtube") && !lowerCase.contains("youtu.be")) {
                return (lowerCase.contains(".m3u8") || lowerCase.contains(".mp4") || lowerCase.contains("master.txt") || lowerCase.contains("/m3u8/")) ? str : ((GxPlayer.isGx(str) || lowerCase.contains("/watch?v=")) && (extract = GxPlayer.extract(str)) != null) ? extract : (!Voe.isVoe(str) || (extract2 = Voe.extract(str)) == null) ? GxPlayer.extract(str) : extract2;
            }
        }
        return null;
    }
}
