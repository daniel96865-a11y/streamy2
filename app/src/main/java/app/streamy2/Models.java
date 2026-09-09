package app.streamy2;

import java.util.ArrayList;
import java.util.List;

/* loaded from: classes.dex */
public final class Models {

    public static class Catalog {
        public List<Category> liveCats = new ArrayList();
        public List<Channel> live = new ArrayList();
        public List<Category> vodCats = new ArrayList();
        public List<Media> vod = new ArrayList();
        public List<Category> seriesCats = new ArrayList();
        public List<Media> series = new ArrayList();
    }

    public static class Channel {
        public boolean archive;
        public int archiveDays;
        public String categoryId;
        public Epg epg;
        public boolean header;
        public String hlsUrl;
        public String id;
        public String logo;
        public String name;
        public int number;
        public String tsUrl;
        public String vavooUrl;
        public String categoryName = "";
        public String epgChannelId = "";
    }

    public static class Epg {
        public long end;
        public long start;
        public String title = "";
        public String nextTitle = "";
    }

    public static class Episode {
        public int episode;
        public String id;
        public int season;
        public String streamUrl;
        public String title;
    }

    public static class Media {
        public String categoryId;
        public String genre;
        public String id;
        public String name;
        public String plot;
        public String poster;
        public boolean series;
        public String streamUrl;
        public String year;
        public String rating = "";
        public String duration = "";
        public String director = "";
        public String cast = "";
        public List<Episode> episodes = new ArrayList();
    }

    public static class Category {
        public String id;
        public String name;

        public Category(String str, String str2) {
            this.id = str;
            this.name = str2;
        }
    }
}
