package app.streamy2;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** User-requested local diagnostics; does not send data or change playback. */
final class PlaybackDiagnostics {
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private static final Handler UI=new Handler(Looper.getMainLooper());

    static void show(Activity activity,String session) {
        AlertDialog dialog=new AlertDialog.Builder(activity)
                .setTitle("Wiedergabe-Diagnose")
                .setMessage("Android-Bericht wird gelesen…")
                .setPositiveButton("OK",null)
                .setNeutralButton("Kopieren",null).show();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
        Context app=activity.getApplicationContext();
        IO.execute(()->{
            String report=report(app,session);
            UI.post(()->{
                if(activity.isFinishing()||activity.isDestroyed()||!dialog.isShowing())return;
                dialog.setMessage(report);
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
                    ClipboardManager clipboard=(ClipboardManager)app.getSystemService(Context.CLIPBOARD_SERVICE);
                    if(clipboard!=null){
                        clipboard.setPrimaryClip(ClipData.newPlainText("Streamy Diagnose",report));
                        Toast.makeText(app,"Bericht kopiert",Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });
    }

    static String report(Context context,String session) {
        StringBuilder out=new StringBuilder("Streamy ").append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.VERSION_CODE).append(")")
                .append("\nAndroid ").append(Build.VERSION.RELEASE).append(" · API ").append(Build.VERSION.SDK_INT)
                .append("\nGerät: ").append(clean(Build.MANUFACTURER+" "+Build.MODEL))
                .append("\n\nAktuelle Sitzung\n").append(clean(session))
                .append("\n\nLetzte Android-Beendigungen\n");
        if(Build.VERSION.SDK_INT<30){
            out.append("Android stellt diesen Verlauf erst ab Android 11 bereit. Die Ursache früherer Abstürze lässt sich hier nicht auslesen.");
        }else{
            try{Api30.append(context,out);}
            catch(Exception e){out.append("Verlauf konnte nicht gelesen werden (").append(e.getClass().getSimpleName()).append(").");}
        }
        out.append("\n\nEinträge können von früheren App-Versionen stammen. Ein fehlender Eintrag schließt einen Absturz nicht aus. Der Bericht wird nur angezeigt und auf Wunsch kopiert.");
        return out.toString();
    }

    static String clean(String value){
        if(value==null)return "—";
        String result=value.replaceAll("(?i)https?://[^\\s]+","[Adresse ausgeblendet]")
                .replaceAll("(?i)(password|passwd|token|signature|username)\\s*[:=]\\s*[^\\s,;]+","$1=[ausgeblendet]");
        return result.length()>2000?result.substring(0,2000)+"…":result;
    }

    private static final class Api30 {
        static void append(Context context,StringBuilder out){
            ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            if(manager==null){out.append("Android-Dienst nicht verfügbar.");return;}
            List<android.app.ApplicationExitInfo> entries=manager.getHistoricalProcessExitReasons(context.getPackageName(),0,10);
            if(entries==null||entries.isEmpty()){out.append("Keine gespeicherten Beendigungen gefunden.");return;}
            SimpleDateFormat format=new SimpleDateFormat("dd.MM.yyyy HH:mm:ss z",Locale.GERMANY);
            int count=0;
            for(android.app.ApplicationExitInfo entry:entries){
                if(entry==null||count++>=10)continue;
                out.append(entry.getTimestamp()>0?format.format(new Date(entry.getTimestamp())):"Zeit nicht gespeichert")
                        .append("\n").append(reason(entry.getReason())).append(" (Code ").append(entry.getReason()).append(")")
                        .append(" · Status/Signal ").append(entry.getStatus()).append('\n');
                if(entry.getPss()>0)out.append("Letzte Speicherstichprobe: ").append(entry.getPss()/1024).append(" MiB PSS\n");
                if(entry.getReason()==2)out.append("Das Signal allein beweist keinen Speichermangel.\n");
                out.append('\n');
            }
        }
    }

    static String reason(int reason){
        switch(reason){
            case 1:return "Prozess hat sich beendet";
            case 2:return "Durch Signal beendet";
            case 3:return "Vom System wegen Speichermangel beendet";
            case 4:return "Java-Absturz (unbehandelte Ausnahme)";
            case 5:return "Nativer Absturz";
            case 6:return "App reagierte nicht (ANR)";
            case 7:return "Start fehlgeschlagen";
            case 8:return "Berechtigungen geändert";
            case 9:return "Übermäßige Ressourcennutzung";
            case 10:return "Vom Nutzer/System auf Nutzeranforderung beendet";
            case 11:return "Nutzerprofil beendet";
            case 12:return "Abhängiger Prozess beendet";
            case 13:return "Andere Systemursache";
            case 14:return "Vom Android-Freezer beendet";
            case 15:return "App-Zustand geändert";
            case 16:return "App-Update (kein Absturznachweis)";
            default:return "Ursache unbekannt";
        }
    }
}
