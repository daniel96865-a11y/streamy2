package app.streamy2;

import app.streamy2.Models;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/* loaded from: classes.dex */
public final class CatalogCache {
    public static File file(File file) {
        return new File(file, "streamy2-live-cache.json");
    }

    public static Models.Catalog read(File file) {
        boolean z;
        if (file == null || !file.exists() || file.length() < 8) {
            return null;
        }
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            int length = (int) file.length();
            byte[] bArr = new byte[length];
            int i = 0;
            while (i < length) {
                int read = fileInputStream.read(bArr, i, length - i);
                if (read < 0) {
                    break;
                }
                i += read;
            }
            fileInputStream.close();
            JSONObject jSONObject = new JSONObject(new String(bArr, 0, i, StandardCharsets.UTF_8));
            Models.Catalog catalog = new Models.Catalog();
            JSONArray optJSONArray = jSONObject.optJSONArray("cats");
            if (optJSONArray != null) {
                for (int i2 = 0; i2 < optJSONArray.length(); i2++) {
                    JSONObject optJSONObject = optJSONArray.optJSONObject(i2);
                    if (optJSONObject != null) {
                        catalog.liveCats.add(new Models.Category(optJSONObject.optString("id"), optJSONObject.optString("name")));
                    }
                }
            }
            JSONArray optJSONArray2 = jSONObject.optJSONArray("live");
            if (optJSONArray2 != null) {
                for (int i3 = 0; i3 < optJSONArray2.length(); i3++) {
                    JSONObject optJSONObject2 = optJSONArray2.optJSONObject(i3);
                    if (optJSONObject2 != null) {
                        Models.Channel channel = new Models.Channel();
                        channel.id = optJSONObject2.optString("id");
                        channel.name = optJSONObject2.optString("name");
                        channel.categoryId = optJSONObject2.optString("cat");
                        channel.categoryName = optJSONObject2.optString("catName");
                        channel.logo = optJSONObject2.optString("logo");
                        channel.epgChannelId = optJSONObject2.optString("epgId");
                        channel.number = optJSONObject2.optInt("num", i3 + 1);
                        channel.hlsUrl = optJSONObject2.optString("hls");
                        channel.tsUrl = optJSONObject2.optString("ts");
                        channel.vavooUrl = optJSONObject2.optString("vavoo");
                        if (channel.vavooUrl != null && channel.vavooUrl.isEmpty()) {
                            channel.vavooUrl = null;
                        }
                        channel.archive = optJSONObject2.optBoolean("archive");
                        channel.archiveDays = optJSONObject2.optInt("days");
                        if (!channel.name.contains("#####") && !channel.name.startsWith("---")) {
                            z = false;
                            channel.header = z;
                            catalog.live.add(channel);
                        }
                        z = true;
                        channel.header = z;
                        catalog.live.add(channel);
                    }
                }
            }
            if (catalog.live.isEmpty()) {
                return null;
            }
            return catalog;
        } catch (Exception unused) {
            return null;
        }
    }

    public static void write(File file, Models.Catalog catalog) {
        if (file == null || catalog == null) {
            return;
        }
        try {
            JSONObject jSONObject = new JSONObject();
            JSONArray jSONArray = new JSONArray();
            for (Models.Category category : catalog.liveCats) {
                JSONObject jSONObject2 = new JSONObject();
                jSONObject2.put("id", category.id);
                jSONObject2.put("name", category.name);
                jSONArray.put(jSONObject2);
            }
            JSONArray jSONArray2 = new JSONArray();
            for (Models.Channel channel : catalog.live) {
                JSONObject jSONObject3 = new JSONObject();
                jSONObject3.put("id", channel.id);
                jSONObject3.put("name", channel.name);
                jSONObject3.put("cat", channel.categoryId);
                jSONObject3.put("catName", channel.categoryName);
                jSONObject3.put("logo", channel.logo);
                jSONObject3.put("epgId", channel.epgChannelId);
                jSONObject3.put("num", channel.number);
                jSONObject3.put("hls", channel.hlsUrl);
                jSONObject3.put("ts", channel.tsUrl);
                if (channel.vavooUrl != null && !channel.vavooUrl.isEmpty()) {
                    jSONObject3.put("vavoo", channel.vavooUrl);
                }
                jSONObject3.put("archive", channel.archive);
                jSONObject3.put("days", channel.archiveDays);
                jSONArray2.put(jSONObject3);
            }
            jSONObject.put("cats", jSONArray);
            jSONObject.put("live", jSONArray2);
            byte[] bytes = jSONObject.toString().getBytes(StandardCharsets.UTF_8);
            File file2 = new File(file.getPath() + ".tmp");
            FileOutputStream fileOutputStream = new FileOutputStream(file2);
            fileOutputStream.write(bytes);
            fileOutputStream.close();
            if (file.exists()) {
                file.delete();
            }
            file2.renameTo(file);
        } catch (Exception unused) {
        }
    }

    private CatalogCache() {
    }
}
