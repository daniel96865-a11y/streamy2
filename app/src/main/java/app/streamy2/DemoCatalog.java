package app.streamy2;

import app.streamy2.Models;

/* loaded from: classes.dex */
public class DemoCatalog {
    private static final String BBB = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4";
    private static final String ELE = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4";
    private static final String ESC = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4";
    private static final String FUN = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4";
    private static final String HLS = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8";
    private static final String JOY = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4";
    private static final String MELT = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4";
    private static final String SINTEL = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4";
    private static final String TEARS = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4";

    public static Models.Catalog build() {
        Models.Catalog catalog = new Models.Catalog();
        catalog.liveCats.add(new Models.Category("general", "DE| GENERAL HD/4K"));
        catalog.liveCats.add(new Models.Category("sport", "DE| SPORT"));
        catalog.liveCats.add(new Models.Category("news", "DE| NEWS"));
        catalog.liveCats.add(new Models.Category("doku", "DE| DOKU"));
        catalog.liveCats.add(new Models.Category("kids", "DE| KIDS"));
        catalog.liveCats.add(new Models.Category("music", "DE| MUSIC"));
        catalog.liveCats.add(new Models.Category("film", "DE| FILM"));
        String[][] strArr = {new String[]{"1", "gen-1", "##### GENERAL HD/4K #####", "general", "flag:de"}, new String[]{"2", "gen-2", "DE: ALPENBLICK HD", "general", "mark:AB"}, new String[]{"3", "gen-3", "DE: ALPENBLICK HD (LOW BIT)", "general", "mark:AB"}, new String[]{"4", "gen-4", "DE: NORDFUNK HD", "general", "mark:NF"}, new String[]{"5", "gen-5", "DE: NORDFUNK RAW", "general", "mark:NF"}, new String[]{"6", "gen-6", "DE: NORDFUNK HD (LOW BIT)", "general", "mark:NF"}, new String[]{"7", "gen-7", "DE: KÜSTENNEO HD", "general", "mark:KN"}, new String[]{"8", "gen-8", "DE: KÜSTENINFO HD", "general", "mark:KI"}, new String[]{"9", "gen-9", "DE: RHEINBLICK 4K", "general", "mark:RB"}, new String[]{"10", "gen-10", "DE: MITTE 1 HD", "general", "mark:M1"}, new String[]{"11", "news-1", "DE: PULSE NEWS HD", "news", "mark:PN"}, new String[]{"12", "news-2", "DE: TAGESSPIEGEL LIVE", "news", "mark:TL"}, new String[]{"13", "sport-1", "DE: LIGA 1 HD", "sport", "mark:L1"}, new String[]{"14", "sport-2", "DE: ARENA SPORT HD", "sport", "mark:AS"}, new String[]{"15", "doku-1", "DE: TERRA REPORT HD", "doku", "mark:TR"}, new String[]{"16", "film-1", "DE: KINO 24 HD", "film", "mark:K2"}, new String[]{"17", "kids-1", "DE: MINI CLUB HD", "kids", "mark:MC"}, new String[]{"18", "music-1", "DE: KLANGMEER HD", "music", "mark:KM"}};
        for (int i = 0; i < 18; i++) {
            String[] strArr2 = strArr[i];
            Models.Channel channel = new Models.Channel();
            channel.number = Integer.parseInt(strArr2[0]);
            channel.id = strArr2[1];
            channel.name = strArr2[2].startsWith("[Demo]") || strArr2[2].contains("#####") ? strArr2[2] : "[Demo] " + strArr2[2];
            channel.categoryId = strArr2[3];
            channel.logo = strArr2[4];
            channel.header = channel.name.contains("#####");
            channel.hlsUrl = HLS;
            channel.tsUrl = HLS;
            channel.categoryName = catName(catalog, channel.categoryId);
            channel.epg = fakeEpg(channel.id);
            catalog.live.add(channel);
        }
        catalog.vodCats.add(new Models.Category("ani", "Animation"));
        catalog.vodCats.add(new Models.Category("drama", "Drama"));
        catalog.vodCats.add(new Models.Category("scifi", "Science-Fiction"));
        catalog.vodCats.add(new Models.Category("abenteuer", "Abenteuer"));
        addVod(catalog, "wiesenlicht", "Wiesenlicht", "ani", "file:///android_asset/posters/nebelwacht.jpg", "2008", "Animation", BBB);
        addVod(catalog, "nebelwacht", "Nebelwacht", "drama", "file:///android_asset/posters/nebelwacht.jpg", "2010", "Fantasy", SINTEL);
        addVod(catalog, "stahltraenen", "Stahltränen", "scifi", "file:///android_asset/posters/stahltraenen.jpg", "2012", "Science-Fiction", TEARS);
        addVod(catalog, "elfenbeintraum", "Elfenbeintraum", "drama", "file:///android_asset/posters/elfenbeintraum.jpg", "2006", "Experimental", ELE);
        addVod(catalog, "fluchtlinie", "Fluchtlinie", "abenteuer", "file:///android_asset/posters/fluchtlinie.jpg", "2016", "Abenteuer", ESC);
        addVod(catalog, "nordkante", "Nordkante", "drama", "file:///android_asset/posters/nordkante.jpg", "2018", "Drama", MELT);
        addVod(catalog, "klangmeer", "Klangmeer", "drama", "file:///android_asset/posters/klangmeer.jpg", "2019", "Drama", FUN);
        addVod(catalog, "freudenfahrt", "Freudenfahrt", "abenteuer", "file:///android_asset/posters/echo-station.jpg", "2017", "Action", JOY);
        catalog.seriesCats.add(new Models.Category("scifi-s", "Science-Fiction"));
        catalog.seriesCats.add(new Models.Category("drama-s", "Drama"));
        addSeries(catalog, "echo-station", "Echo Station", "scifi-s", "file:///android_asset/posters/echo-station.jpg", "2024", "Science-Fiction", TEARS, SINTEL, BBB);
        addSeries(catalog, "hafenlicht", "Hafenlicht", "drama-s", "file:///android_asset/posters/nordkante.jpg", "2023", "Drama", ELE, BBB, FUN);
        return catalog;
    }

    private static String catName(Models.Catalog catalog, String str) {
        for (Models.Category category : catalog.liveCats) {
            if (category.id.equals(str)) {
                return category.name;
            }
        }
        return "";
    }

    private static void addVod(Models.Catalog catalog, String str, String str2, String str3, String str4, String str5, String str6, String str7) {
        Models.Media media = new Models.Media();
        media.id = str;
        media.name = str2.startsWith("[Demo]") ? str2 : "[Demo] " + str2;
        media.categoryId = str3;
        media.poster = str4;
        media.year = str5;
        media.genre = str6;
        media.streamUrl = str7;
        catalog.vod.add(media);
    }

    private static void addSeries(Models.Catalog catalog, String str, String str2, String str3, String str4, String str5, String str6, String... strArr) {
        Models.Media media = new Models.Media();
        media.id = str;
        media.name = str2.startsWith("[Demo]") ? str2 : "[Demo] " + str2;
        media.categoryId = str3;
        media.poster = str4;
        media.year = str5;
        media.genre = str6;
        media.series = true;
        for (int i = 1; i <= 2; i++) {
            int i2 = 0;
            while (i2 < strArr.length) {
                Models.Episode episode = new Models.Episode();
                int i3 = i2 + 1;
                episode.id = str + "-s" + i + "e" + i3;
                episode.title = "Folge " + i3;
                episode.season = i;
                episode.episode = i3;
                episode.streamUrl = strArr[i2];
                media.episodes.add(episode);
                i2 = i3;
            }
        }
        catalog.series.add(media);
    }

    public static Models.Epg fakeEpg(String str) {
        String[] strArr = {"Morgenmagazin", "Nachrichten", "Mittagsreport", "Kulturzeit", "Talk am Abend", "Spielfilm"};
        int hashCode = str.hashCode();
        Models.Epg epg = new Models.Epg();
        epg.title = strArr[Math.abs(hashCode) % 6];
        epg.start = System.currentTimeMillis() - (((Math.abs(hashCode) % 50) + 20) * 60000);
        epg.end = epg.start + (((Math.abs(hashCode) % 50) + 40) * 60000);
        epg.nextTitle = strArr[Math.abs(hashCode / 3) % 6];
        return epg;
    }
}
