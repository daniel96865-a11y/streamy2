package app.streamy2;

import android.util.Xml;
import app.streamy2.EpgGuide;
import app.streamy2.Models;
import com.google.common.net.HttpHeaders;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import org.xmlpull.v1.XmlPullParser;

/* loaded from: classes.dex */
public class EpgGuide {
    public volatile int channelCount;
    public volatile String error;
    public volatile boolean loading;
    public volatile int programmeCount;
    private final ConcurrentHashMap<String, List<Listing>> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> nameToId = new ConcurrentHashMap<>();

    public static class Listing {
        public long start;
        public long stop;
        public String title = "";
        public String desc = "";
    }

    public void clear() {
        this.byId.clear();
        this.nameToId.clear();
        this.programmeCount = 0;
        this.channelCount = 0;
        this.error = null;
    }

    public Models.Epg forChannel(Models.Channel channel) {
        Models.Epg lookup;
        Models.Epg lookup2;
        if (channel == null) {
            return null;
        }
        Models.Epg lookup3 = lookup(channel.epgChannelId);
        if (lookup3 != null) {
            return lookup3;
        }
        if (channel.epgChannelId != null && channel.epgChannelId.contains("@") && (lookup2 = lookup(channel.epgChannelId.substring(0, channel.epgChannelId.indexOf(64)))) != null) {
            return lookup2;
        }
        Models.Epg lookup4 = lookup(channel.id);
        if (lookup4 != null) {
            return lookup4;
        }
        if (channel.id != null && channel.id.startsWith("iptv:") && (lookup = lookup(channel.id.substring(5))) != null) {
            return lookup;
        }
        String findNameId = findNameId(normName(channel.name));
        if (findNameId == null) {
            return null;
        }
        channel.epgChannelId = findNameId;
        return current(findNameId);
    }

    public List<Listing> listingsFor(Models.Channel channel) {
        List<Listing> list;
        ArrayList arrayList = new ArrayList();
        if (channel == null) {
            return arrayList;
        }
        String keyOf = keyOf(channel);
        if (keyOf != null && (list = this.byId.get(keyOf)) != null) {
            arrayList.addAll(list);
        }
        arrayList.sort(new Comparator() { // from class: app.streamy2.EpgGuide$$ExternalSyntheticLambda1
            @Override // java.util.Comparator
            public final int compare(Object obj, Object obj2) {
                int compare;
                compare = Long.compare(((EpgGuide.Listing) obj).start, ((EpgGuide.Listing) obj2).start);
                return compare;
            }
        });
        return arrayList;
    }

    public void putListings(Models.Channel channel, List<Listing> list) {
        if (channel == null || list == null || list.isEmpty()) {
            return;
        }
        String norm = norm((channel.epgChannelId == null || channel.epgChannelId.isEmpty()) ? channel.id : channel.epgChannelId);
        if (norm.isEmpty()) {
            norm = norm(channel.id);
        }
        List<Listing> list2 = this.byId.get(norm);
        HashMap hashMap = new HashMap();
        if (list2 != null) {
            for (Listing listing : list2) {
                hashMap.put(listing.start + "|" + listing.stop, listing);
            }
        }
        for (Listing listing2 : list) {
            hashMap.put(listing2.start + "|" + listing2.stop, listing2);
        }
        ArrayList arrayList = new ArrayList(hashMap.values());
        arrayList.sort(new Comparator() { // from class: app.streamy2.EpgGuide$$ExternalSyntheticLambda0
            @Override // java.util.Comparator
            public final int compare(Object obj, Object obj2) {
                int compare;
                compare = Long.compare(((EpgGuide.Listing) obj).start, ((EpgGuide.Listing) obj2).start);
                return compare;
            }
        });
        this.byId.put(norm, arrayList);
        if (channel.name != null) {
            this.nameToId.put(normName(channel.name), norm);
        }
        this.channelCount = this.byId.size();
        Iterator<List<Listing>> it = this.byId.values().iterator();
        int i = 0;
        while (it.hasNext()) {
            i += it.next().size();
        }
        this.programmeCount = i;
    }

    private String keyOf(Models.Channel channel) {
        if (channel.epgChannelId != null && !channel.epgChannelId.isEmpty()) {
            String norm = norm(channel.epgChannelId);
            if (this.byId.containsKey(norm)) {
                return norm;
            }
            int indexOf = norm.indexOf(64);
            if (indexOf > 0 && this.byId.containsKey(norm.substring(0, indexOf))) {
                return norm.substring(0, indexOf);
            }
        }
        return (channel.id == null || !this.byId.containsKey(norm(channel.id))) ? findNameId(normName(channel.name)) : norm(channel.id);
    }

    private String findNameId(String str) {
        String str2;
        if (str != null && !str.isEmpty()) {
            String str3 = this.nameToId.get(str);
            if (str3 != null) {
                return str3;
            }
            String str4 = this.nameToId.get(str.replace(" ", ""));
            if (str4 != null) {
                return str4;
            }
            for (String str5 : aliases(str)) {
                String str6 = this.nameToId.get(str5);
                if (str6 != null) {
                    return str6;
                }
            }
            String[] split = str.split(" ");
            if (split.length >= 2 && (str2 = this.nameToId.get(split[0] + " " + split[1])) != null) {
                return str2;
            }
        }
        return null;
    }

    private static void indexName(Map<String, String> map, String str, String str2) {
        String normName = normName(str);
        if (normName.isEmpty()) {
            return;
        }
        putFree(map, normName, str2);
        putFree(map, normName.replace(" ", ""), str2);
        for (String str3 : aliases(normName)) {
            putFree(map, str3, str2);
        }
        String[] split = normName.split(" ");
        if (split.length >= 3) {
            putFree(map, split[0] + " " + split[1], str2);
        }
    }

    private static void putFree(Map<String, String> map, String str, String str2) {
        if (str == null || str.length() < 2 || str2 == null || map.containsKey(str)) {
            return;
        }
        map.put(str, str2);
    }

    public int apply(List<Models.Channel> list) {
        int i = 0;
        if (list == null) {
            return 0;
        }
        try {
            for (Models.Channel channel : new ArrayList<Models.Channel>(list)) {
                try {
                    Models.Epg forChannel = forChannel(channel);
                    if (forChannel != null) {
                        channel.epg = forChannel;
                        i++;
                    }
                } catch (Exception unused) {
                }
            }
        } catch (Exception unused2) {
        }
        return i;
    }

    public void loadUrl(String str, File file, boolean z) throws Exception {
        loadUrl(str, file, z, false);
    }

    public void loadUrlMerge(String str, File file, boolean z) throws Exception {
        loadUrl(str, file, z, true);
    }

    private void loadUrl(String str, File file, boolean z, boolean z2) throws Exception {
        if (str == null || str.trim().isEmpty()) {
            throw new Exception("Keine EPG-URL");
        }
        String trim = str.trim();
        if (file != null && file.exists() && file.length() > 12582912) {
            file.delete();
        }
        if (!z && file != null && file.exists() && file.length() > 200) {
            parseFile(file, z2);
            if (this.channelCount > 0) {
                return;
            }
        }
        download(trim, file);
        parseFile(file, z2);
        if (this.channelCount == 0) {
            throw new Exception("XMLTV ohne Programme");
        }
    }

    public void loadFile(File file) throws Exception {
        parseFile(file, false);
    }

    private void download(String str, File file) throws Exception {
        HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
        httpURLConnection.setConnectTimeout(10000);
        httpURLConnection.setReadTimeout(120000);
        httpURLConnection.setInstanceFollowRedirects(true);
        httpURLConnection.setRequestProperty(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36");
        httpURLConnection.setRequestProperty(HttpHeaders.ACCEPT, "application/xml,text/xml,application/gzip,*/*");
        httpURLConnection.setRequestProperty(HttpHeaders.ACCEPT_ENCODING, "identity");
        int responseCode = httpURLConnection.getResponseCode();
        InputStream errorStream = responseCode >= 400 ? httpURLConnection.getErrorStream() : httpURLConnection.getInputStream();
        if (errorStream == null) {
            throw new Exception("HTTP " + responseCode);
        }
        String contentEncoding = httpURLConnection.getContentEncoding();
        boolean z = contentEncoding != null && contentEncoding.toLowerCase(Locale.US).contains("gzip");
        boolean contains = str.toLowerCase(Locale.US).contains(".gz");
        if (z && !contains) {
            try {
                errorStream = new GZIPInputStream(errorStream);
            } catch (Exception unused) {
            }
        }
        File parentFile = file.getParentFile();
        if (parentFile != null && !parentFile.exists()) {
            parentFile.mkdirs();
        }
        File file2 = new File(file.getPath() + ".part");
        FileOutputStream fileOutputStream = new FileOutputStream(file2);
        byte[] bArr = new byte[16384];
        long j = 0;
        do {
            try {
                int read = errorStream.read(bArr);
                if (read < 0) {
                    if (responseCode >= 400 || j < 40) {
                        file2.delete();
                        throw new Exception("HTTP " + responseCode);
                    }
                    if (file.exists()) {
                        file.delete();
                    }
                    if (!file2.renameTo(file)) {
                        throw new Exception("EPG-Cache fehlgeschlagen");
                    }
                    return;
                }
                fileOutputStream.write(bArr, 0, read);
                j += read;
            } finally {
                try {
                    fileOutputStream.close();
                } catch (Exception unused2) {
                }
                try {
                    errorStream.close();
                } catch (Exception unused3) {
                }
                httpURLConnection.disconnect();
            }
        } while (j <= 94371840);
        throw new Exception("EPG-Datei zu groß");
    }

    private void parseFile(File file, boolean z) throws Exception {
        if (file == null || !file.exists()) {
            throw new Exception("Kein EPG-Cache");
        }
        InputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(file), 16384);
        try {
            bufferedInputStream.mark(4);
            int read = bufferedInputStream.read();
            int read2 = bufferedInputStream.read();
            bufferedInputStream.reset();
            if (read == 31 && read2 == 139) {
                bufferedInputStream = new GZIPInputStream(bufferedInputStream);
            }
            parse(bufferedInputStream, z);
        } finally {
            try {
                bufferedInputStream.close();
            } catch (Exception unused) {
            }
        }
    }

    private void parse(InputStream inputStream, boolean z) throws Exception {
        HashMap hashMap;
        String str;
        XmlPullParser newPullParser = Xml.newPullParser();
        int i = 0;
        newPullParser.setFeature("http://xmlpull.org/v1/doc/features.html#process-namespaces", false);
        newPullParser.setInput(inputStream, "UTF-8");
        HashMap hashMap2 = new HashMap();
        HashMap hashMap3 = new HashMap();
        long currentTimeMillis = System.currentTimeMillis();
        long j = currentTimeMillis - 7200000;
        long j2 = currentTimeMillis + 28800000;
        int eventType = newPullParser.getEventType();
        String str2 = null;
        String str3 = null;
        while (eventType != 1) {
            int i2 = 2;
            if (eventType != 2) {
                hashMap = hashMap3;
                str = str3;
                if (eventType == 3 && "channel".equals(newPullParser.getName())) {
                    str3 = null;
                }
                str3 = str;
            } else {
                String name = newPullParser.getName();
                if ("channel".equals(name)) {
                    hashMap = hashMap3;
                    str3 = newPullParser.getAttributeValue(str2, "id");
                } else {
                    if ("display-name".equals(name) && str3 != null) {
                        indexName(hashMap3, text(newPullParser), norm(str3));
                        indexName(hashMap3, str3, norm(str3));
                        int indexOf = str3.indexOf(64);
                        if (indexOf > 0) {
                            indexName(hashMap3, str3.substring(i, indexOf), norm(str3));
                        }
                    } else if ("programme".equals(name)) {
                        String attributeValue = newPullParser.getAttributeValue(str2, "channel");
                        hashMap = hashMap3;
                        long parseXmltvTime = parseXmltvTime(newPullParser.getAttributeValue(str2, "start"));
                        str = str3;
                        long parseXmltvTime2 = parseXmltvTime(newPullParser.getAttributeValue(str2, "stop"));
                        boolean z2 = attributeValue != null && parseXmltvTime > 0 && parseXmltvTime2 > parseXmltvTime && parseXmltvTime2 >= j && parseXmltvTime <= j2;
                        String str4 = "";
                        int i3 = 1;
                        while (i3 > 0) {
                            int next = newPullParser.next();
                            if (next == i2) {
                                if (z2 && str4.isEmpty() && "title".equals(newPullParser.getName())) {
                                    str4 = text(newPullParser);
                                } else {
                                    i3++;
                                }
                            } else if (next == 3) {
                                i3--;
                            } else if (next == 1) {
                                break;
                            } else {
                                i2 = 2;
                            }
                            i2 = 2;
                        }
                        if (z2 && str4 != null && !str4.isEmpty()) {
                            Listing listing = new Listing();
                            listing.title = str4.trim();
                            listing.start = parseXmltvTime;
                            listing.stop = parseXmltvTime2;
                            String norm = norm(attributeValue);
                            List list = (List) hashMap2.get(norm);
                            if (list == null) {
                                list = new ArrayList();
                                hashMap2.put(norm, list);
                            }
                            if (list.size() < 3) {
                                list.add(listing);
                            }
                        }
                        str3 = str;
                    }
                    hashMap = hashMap3;
                    str = str3;
                    str3 = str;
                }
            }
            eventType = newPullParser.next();
            hashMap3 = hashMap;
            i = 0;
            str2 = null;
        }
        HashMap hashMap4 = hashMap3;
        if (!z) {
            this.byId.clear();
            this.nameToId.clear();
        }
        this.byId.putAll(hashMap2);
        this.nameToId.putAll(hashMap4);
        Iterator<List<Listing>> it = this.byId.values().iterator();
        int i4 = 0;
        while (it.hasNext()) {
            i4 += it.next().size();
        }
        this.programmeCount = i4;
        this.channelCount = this.byId.size();
        this.error = null;
    }

    private Models.Epg lookup(String str) {
        if (str == null || str.isEmpty() || "null".equalsIgnoreCase(str)) {
            return null;
        }
        return current(norm(str));
    }

    private Models.Epg current(String str) {
        List<Listing> list = this.byId.get(str);
        if (list == null || list.isEmpty()) {
            return null;
        }
        long currentTimeMillis = System.currentTimeMillis();
        Listing listing = null;
        Listing listing2 = null;
        for (Listing listing3 : list) {
            if (listing3.start <= currentTimeMillis && listing3.stop > currentTimeMillis) {
                listing = listing3;
            } else if (listing3.start > currentTimeMillis && (listing2 == null || listing3.start < listing2.start)) {
                listing2 = listing3;
            }
        }
        if (listing == null) {
            Iterator<Listing> it = list.iterator();
            while (true) {
                if (!it.hasNext()) {
                    break;
                }
                Listing next = it.next();
                if (next.stop > currentTimeMillis) {
                    listing = next;
                    break;
                }
            }
        }
        if (listing == null) {
            listing = list.get(0);
        }
        Models.Epg epg = new Models.Epg();
        epg.title = listing.title;
        epg.start = listing.start;
        epg.end = listing.stop;
        if (listing2 != null) {
            epg.nextTitle = listing2.title;
        }
        if (epg.title == null || epg.title.isEmpty()) {
            return null;
        }
        return epg;
    }

    private static String text(XmlPullParser xmlPullParser) throws Exception {
        String nextText = xmlPullParser.nextText();
        return nextText == null ? "" : nextText.trim();
    }

    static long parseXmltvTime(String str) {
        if (str == null) {
            return 0L;
        }
        String trim = str.trim();
        if (trim.length() < 14) {
            return 0L;
        }
        try {
            try {
                if (trim.length() >= 18) {
                    return new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).parse(trim).getTime();
                }
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
                simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                return simpleDateFormat.parse(trim.substring(0, 14)).getTime();
            } catch (Exception unused) {
                return new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).parse(trim.substring(0, 14)).getTime();
            }
        } catch (Exception unused2) {
            return 0L;
        }
    }

    static String norm(String str) {
        return str == null ? "" : str.trim().toLowerCase(Locale.US);
    }

    static String normName(String str) {
        if (str == null) {
            return "";
        }
        return str.toLowerCase(Locale.GERMAN).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss").replace("sat.1", "sat1").replace("sat 1", "sat1").replace("3sat", "3sat").replace("3 sat", "3sat").replaceAll("\\[.*?\\]", " ").replaceAll("\\([^)]*\\)", " ").replaceAll("\\s*\\.[bcsf]\\b", " ").replaceFirst("^de:\\s*", "").replaceAll("\\b(fhd|uhd|hd\\+|hdtv|sd|4k|hevc|raw|hq|backup|germany|deutsch|german|universal)\\b", " ").replaceAll("(?<!\\w)hd(?!\\w)", " ").replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static String[] aliases(String str) {
        if (str == null) {
            return new String[0];
        }
        if ("rtl 2".equals(str) || "rtl ii".equals(str) || "rtlzwei".equals(str) || "rtl2".equals(str)) {
            return new String[]{"rtl 2", "rtlzwei", "rtl2"};
        }
        if ("kabel eins".equals(str) || "kabeleins".equals(str) || "kabel 1".equals(str)) {
            return new String[]{"kabel eins", "kabeleins", "kabel 1"};
        }
        if ("sat1".equals(str) || "sat 1".equals(str)) {
            return new String[]{"sat1", "sat 1"};
        }
        if ("3sat".equals(str) || "3 sat".equals(str)) {
            return new String[]{"3sat", "3 sat"};
        }
        if ("prosieben".equals(str) || "pro sieben".equals(str)) {
            return new String[]{"prosieben", "pro sieben"};
        }
        if ("das erste".equals(str) || "ard".equals(str)) {
            return new String[]{"das erste", "ard"};
        }
        if ("13th street".equals(str) || "13th street universal".equals(str)) {
            return new String[]{"13th street", "13th street universal"};
        }
        if ("123 tv".equals(str) || "1 2 3 tv".equals(str) || "123tv".equals(str)) {
            return new String[]{"123 tv", "1 2 3 tv", "123tv"};
        }
        if ("nick".equals(str) || "nickelodeon".equals(str)) {
            return new String[]{"nick", "nickelodeon"};
        }
        if ("disney channel".equals(str) || "disney".equals(str)) {
            return new String[]{"disney channel", "disney"};
        }
        if ("rtl up".equals(str) || "rtlup".equals(str)) {
            return new String[]{"rtlup", "rtl up"};
        }
        if ("vox up".equals(str) || "voxup".equals(str)) {
            return new String[]{"voxup", "vox up"};
        }
        return new String[0];
    }
}
