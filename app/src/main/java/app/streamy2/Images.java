package app.streamy2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.media3.common.PlaybackException;
import com.google.common.net.HttpHeaders;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/* loaded from: classes.dex */
public class Images {
    private static final int GIF_MAX = 160;
    private static volatile boolean scrolling;
    private static final LruCache<String, Bitmap> BITMAPS = new LruCache<String, Bitmap>(25165824) { // from class: app.streamy2.Images.1
        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.util.LruCache
        public int sizeOf(String str, Bitmap bitmap) {
            if (bitmap == null) {
                return 1;
            }
            return bitmap.getByteCount();
        }
    };
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final List<AnimatedImageDrawable> RUNNING = new ArrayList();

    private static int bucket(int i, boolean z) {
        return (!z && i > 200) ? i <= 360 ? 320 : 480 : GIF_MAX;
    }

    public static void resume(ImageView imageView) {
    }

    public static void setScrolling(boolean z) {
        if (scrolling == z) {
            return;
        }
        scrolling = z;
        if (z) {
            pauseAll();
        }
    }

    public static void load(final ImageView imageView, final String str) {
        if (imageView == null) {
            return;
        }
        final int bucket = bucket(sizeOf(imageView), looksGif(str));
        final String str2 = str + "#" + bucket;
        if (str2.equals(imageView.getTag()) && imageView.getDrawable() != null) {
            if (scrolling) {
                return;
            }
            maybeStart(imageView);
            return;
        }
        stopAnim(imageView);
        imageView.setTag(str2);
        if (str == null || str.isEmpty()) {
            imageView.setImageDrawable(null);
            return;
        }
        Bitmap bitmap = BITMAPS.get(str2);
        if (bitmap != null && !bitmap.isRecycled()) {
            imageView.setImageBitmap(bitmap);
            if (scrolling || !looksGif(str)) {
                return;
            }
            decodeAnim(imageView, str, str2, null, bucket);
            return;
        }
        imageView.setImageDrawable(null);
        IO.execute(new Runnable() { // from class: app.streamy2.Images$$ExternalSyntheticLambda3
            @Override // java.lang.Runnable
            public final void run() {
                Images.lambda$load$1(imageView, str, bucket, str2);
            }
        });
    }

    static /* synthetic */ void lambda$load$1(final ImageView imageView, String str, int i, final String str2) {
        byte[] readCached = readCached(imageView.getContext(), str);
        if (readCached == null) {
            readCached = download(imageView.getContext(), str);
        }
        if (readCached == null || readCached.length == 0) {
            return;
        }
        final Bitmap decodeStill = decodeStill(readCached, i);
        if (decodeStill != null) {
            BITMAPS.put(str2, decodeStill);
        }
        UI.post(new Runnable() { // from class: app.streamy2.Images$$ExternalSyntheticLambda4
            @Override // java.lang.Runnable
            public final void run() {
                Images.lambda$load$0(str2, imageView, decodeStill);
            }
        });
    }

    static /* synthetic */ void lambda$load$0(String str, ImageView imageView, Bitmap bitmap) {
        if (str.equals(imageView.getTag()) && bitmap != null) {
            imageView.setImageBitmap(bitmap);
        }
    }

    public static void unbind(ImageView imageView) {
        if (imageView == null) {
            return;
        }
        stopAnim(imageView);
    }

    private static void decodeAnim(final ImageView imageView, final String str, final String str2, final byte[] bArr, final int i) {
        if (Build.VERSION.SDK_INT < 28 || imageView == null || str == null) {
            return;
        }
        IO.execute(new Runnable() { // from class: app.streamy2.Images$$ExternalSyntheticLambda2
            @Override // java.lang.Runnable
            public final void run() {
                Images.lambda$decodeAnim$4(bArr, imageView, str, i, str2);
            }
        });
    }

    static /* synthetic */ void lambda$decodeAnim$4(byte[] bArr, final ImageView imageView, String str, int i, final String str2) {
        try {
            File writeLogo = bArr != null ? writeLogo(imageView.getContext(), str, bArr) : existingLogo(imageView.getContext(), str);
            if (writeLogo != null && writeLogo.exists()) {
                final int max = Math.max(GIF_MAX, i);
                final Drawable decodeDrawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(writeLogo), new ImageDecoder.OnHeaderDecodedListener() { // from class: app.streamy2.Images$$ExternalSyntheticLambda0
                    @Override // android.graphics.ImageDecoder.OnHeaderDecodedListener
                    public final void onHeaderDecoded(ImageDecoder imageDecoder, ImageDecoder.ImageInfo imageInfo, ImageDecoder.Source source) {
                        Images.lambda$decodeAnim$2(max, imageDecoder, imageInfo, source);
                    }
                });
                if (decodeDrawable instanceof AnimatedImageDrawable) {
                    UI.post(new Runnable() { // from class: app.streamy2.Images$$ExternalSyntheticLambda1
                        @Override // java.lang.Runnable
                        public final void run() {
                            Images.lambda$decodeAnim$3(str2, imageView, decodeDrawable);
                        }
                    });
                }
            }
        } catch (Exception unused) {
        }
    }

    static /* synthetic */ void lambda$decodeAnim$2(int i, ImageDecoder imageDecoder, ImageDecoder.ImageInfo imageInfo, ImageDecoder.Source source) {
        imageDecoder.setAllocator(1);
        float f = i;
        float max = Math.max(1, imageInfo.getSize().getWidth());
        float max2 = Math.max(1, imageInfo.getSize().getHeight());
        float min = Math.min(f / max, f / max2);
        if (min < 1.0f) {
            imageDecoder.setTargetSize(Math.max(1, (int) (max * min)), Math.max(1, (int) (max2 * min)));
        }
    }

    static /* synthetic */ void lambda$decodeAnim$3(String str, ImageView imageView, Drawable drawable) {
        if (!str.equals(imageView.getTag()) || scrolling) {
            return;
        }
        AnimatedImageDrawable animatedImageDrawable = (AnimatedImageDrawable) drawable;
        imageView.setImageDrawable(animatedImageDrawable);
        startLimited(animatedImageDrawable);
    }

    private static void maybeStart(ImageView imageView) {
        Drawable drawable = imageView.getDrawable();
        if (Build.VERSION.SDK_INT < 28 || !(drawable instanceof AnimatedImageDrawable)) {
            return;
        }
        startLimited((AnimatedImageDrawable) drawable);
    }

    private static void startLimited(AnimatedImageDrawable animatedImageDrawable) {
        if (animatedImageDrawable.isRunning()) {
            return;
        }
        List<AnimatedImageDrawable> list = RUNNING;
        synchronized (list) {
            Iterator<AnimatedImageDrawable> it = list.iterator();
            while (it.hasNext()) {
                if (!it.next().isRunning()) {
                    it.remove();
                }
            }
            while (true) {
                List<AnimatedImageDrawable> list2 = RUNNING;
                if (list2.size() >= 4) {
                    try {
                        list2.remove(0).stop();
                    } catch (Exception unused) {
                    }
                } else {
                    list2.add(animatedImageDrawable);
                    break;
                }
            }
        }
        try {
            animatedImageDrawable.setRepeatCount(-1);
            animatedImageDrawable.start();
        } catch (Exception unused2) {
        }
    }

    private static void pauseAll() {
        List<AnimatedImageDrawable> list = RUNNING;
        synchronized (list) {
            Iterator<AnimatedImageDrawable> it = list.iterator();
            while (it.hasNext()) {
                try {
                    it.next().stop();
                } catch (Exception unused) {
                }
            }
            RUNNING.clear();
        }
    }

    private static void stopAnim(ImageView imageView) {
        Drawable drawable = imageView.getDrawable();
        if (Build.VERSION.SDK_INT < 28 || !(drawable instanceof AnimatedImageDrawable)) {
            return;
        }
        try {
            ((AnimatedImageDrawable) drawable).stop();
        } catch (Exception unused) {
        }
        List<AnimatedImageDrawable> list = RUNNING;
        synchronized (list) {
            list.remove(drawable);
        }
    }

    private static Bitmap decodeStill(byte[] bArr, int i) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        int i2 = 1;
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bArr, 0, bArr.length, options);
        options.inJustDecodeBounds = false;
        int max = Math.max(128, i);
        options.inPreferredConfig = max <= 200 ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888;
        options.inSampleSize = 1;
        if (options.outWidth > max || options.outHeight > max) {
            while (true) {
                int i3 = i2 * 2;
                if (options.outWidth / i3 < max || options.outHeight / i3 < max) {
                    break;
                }
                i2 = i3;
            }
            options.inSampleSize = i2;
        }
        try {
            return BitmapFactory.decodeByteArray(bArr, 0, bArr.length, options);
        } catch (Throwable unused) {
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            try {
                return BitmapFactory.decodeByteArray(bArr, 0, bArr.length, options);
            } catch (Throwable unused2) {
                return null;
            }
        }
    }

    private static int sizeOf(ImageView imageView) {
        int width = imageView.getWidth();
        int height = imageView.getHeight();
        ViewGroup.LayoutParams layoutParams = imageView.getLayoutParams();
        if (width <= 0 && layoutParams != null && layoutParams.width > 0) {
            width = layoutParams.width;
        }
        if (height <= 0 && layoutParams != null && layoutParams.height > 0) {
            height = layoutParams.height;
        }
        float f = imageView.getResources().getDisplayMetrics().density;
        if (width <= 0) {
            width = Math.round(f * 48.0f);
        }
        if (height <= 0) {
            height = width;
        }
        return Math.max(width, height);
    }

    private static byte[] download(Context context, String str) {
        try {
            if (str.startsWith("file:///android_asset/")) {
                InputStream open = context.getAssets().open(str.substring(22));
                byte[] readAll = readAll(open);
                open.close();
                return readAll;
            }
            if (!str.startsWith("http")) {
                return null;
            }
            HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
            httpURLConnection.setConnectTimeout(6001);
            httpURLConnection.setReadTimeout(8000);
            httpURLConnection.setInstanceFollowRedirects(true);
            httpURLConnection.setRequestProperty(HttpHeaders.USER_AGENT, "Mozilla/5.0");
            InputStream inputStream = httpURLConnection.getInputStream();
            byte[] readAll2 = readAll(inputStream);
            inputStream.close();
            httpURLConnection.disconnect();
            if (readAll2 != null && readAll2.length > 0) {
                writeLogo(context, str, readAll2);
            }
            return readAll2;
        } catch (Exception unused) {
            return null;
        }
    }

    private static byte[] readCached(Context context, String str) {
        File existingLogo = existingLogo(context, str);
        if (existingLogo == null) {
            return null;
        }
        try {
            FileInputStream fileInputStream = new FileInputStream(existingLogo);
            byte[] readAll = readAll(fileInputStream);
            fileInputStream.close();
            if (readAll == null) {
                return null;
            }
            if (readAll.length > 0) {
                return readAll;
            }
            return null;
        } catch (Exception unused) {
            return null;
        }
    }

    private static File existingLogo(Context context, String str) {
        File file = new File(new File(context.getCacheDir(), "logos"), Integer.toHexString(str.hashCode()) + ".img");
        if (!file.exists() || file.length() <= 8) {
            return null;
        }
        return file;
    }

    private static File writeLogo(Context context, String str, byte[] bArr) throws Exception {
        File file = new File(context.getCacheDir(), "logos");
        if (!file.exists()) {
            file.mkdirs();
        }
        File file2 = new File(file, Integer.toHexString(str.hashCode()) + ".img");
        if (file2.exists() && file2.length() == bArr.length) {
            return file2;
        }
        File file3 = new File(file2.getPath() + ".tmp");
        FileOutputStream fileOutputStream = new FileOutputStream(file3);
        fileOutputStream.write(bArr);
        fileOutputStream.close();
        if (file2.exists()) {
            file2.delete();
        }
        file3.renameTo(file2);
        return file2;
    }

    private static boolean looksGif(String str) {
        return str.toLowerCase().contains(".gif");
    }

    private static boolean isGif(byte[] bArr) {
        return bArr != null && bArr.length > 5 && bArr[0] == 71 && bArr[1] == 73 && bArr[2] == 70;
    }

    private static byte[] readAll(InputStream inputStream) throws Exception {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] bArr = new byte[8192];
        int i = 0;
        do {
            int read = inputStream.read(bArr);
            if (read < 0) {
                break;
            }
            byteArrayOutputStream.write(bArr, 0, read);
            i += read;
        } while (i <= 4000000);
        return byteArrayOutputStream.toByteArray();
    }
}
