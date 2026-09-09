package app.streamy2;

import androidx.media3.common.PlaybackException;
import com.google.common.net.HttpHeaders;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Random;
import org.json.JSONObject;

/* loaded from: classes.dex */
final class Pairing {
    static final int HTTP = 38473;
    static final int UDP = 38472;
    private static Thread httpT;
    private static volatile String payload;
    private static volatile String pin;
    private static volatile boolean running;
    private static ServerSocket server;
    private static DatagramSocket udp;
    private static Thread udpT;

    Pairing() {
    }

    static synchronized String startHost(String str) {
        String str2;
        synchronized (Pairing.class) {
            stop();
            pin = String.format(Locale.US, "%06d", Integer.valueOf(new Random().nextInt(900000) + 102400));
            if (str == null) {
                str = "{}";
            }
            payload = str;
            running = true;
            httpT = new Thread(new Runnable() { // from class: app.streamy2.Pairing$$ExternalSyntheticLambda0
                @Override // java.lang.Runnable
                public final void run() {
                    Pairing.httpLoop();
                }
            }, "s2-pair-http");
            udpT = new Thread(new Runnable() { // from class: app.streamy2.Pairing$$ExternalSyntheticLambda1
                @Override // java.lang.Runnable
                public final void run() {
                    Pairing.udpLoop();
                }
            }, "s2-pair-udp");
            httpT.setDaemon(true);
            udpT.setDaemon(true);
            httpT.start();
            udpT.start();
            str2 = pin;
        }
        return str2;
    }

    static synchronized void stop() {
        synchronized (Pairing.class) {
            running = false;
            pin = null;
            payload = null;
            try {
                ServerSocket serverSocket = server;
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (Exception unused) {
            }
            try {
                DatagramSocket datagramSocket = udp;
                if (datagramSocket != null) {
                    datagramSocket.close();
                }
            } catch (Exception unused2) {
            }
            server = null;
            udp = null;
            httpT = null;
            udpT = null;
        }
    }

    static boolean hosting() {
        return running && pin != null;
    }

    static String fetch(String str, int i) {
        DatagramSocket datagramSocket = null;
        String str2;
        DatagramSocket datagramSocket2 = null;
        if (str == null) {
            return null;
        }
        String trim = str.trim();
        if (trim.length() < 4) {
            return null;
        }
        long currentTimeMillis = System.currentTimeMillis() + Math.max(4000, i);
        try {
            datagramSocket = new DatagramSocket();
            try {
                try {
                    datagramSocket.setBroadcast(true);
                    datagramSocket.setSoTimeout(600);
                    byte[] bytes = ("S2WANT " + trim).getBytes(StandardCharsets.UTF_8);
                    DatagramPacket datagramPacket = new DatagramPacket(bytes, bytes.length, InetAddress.getByName("255.255.255.255"), UDP);
                    int i2 = 512;
                    byte[] bArr = new byte[512];
                    while (true) {
                        if (System.currentTimeMillis() >= currentTimeMillis) {
                            str2 = null;
                            break;
                        }
                        try {
                            datagramSocket.send(datagramPacket);
                        } catch (Exception unused) {
                        }
                        try {
                            DatagramPacket datagramPacket2 = new DatagramPacket(bArr, i2);
                            datagramSocket.receive(datagramPacket2);
                            String trim2 = new String(datagramPacket2.getData(), 0, datagramPacket2.getLength(), StandardCharsets.UTF_8).trim();
                            if (trim2.startsWith("S2OFFER ")) {
                                String[] split = trim2.split("\\s+");
                                if (split.length >= 4 && trim.equals(split[1])) {
                                    str2 = split[2];
                                    break;
                                }
                            } else {
                                continue;
                            }
                        } catch (SocketTimeoutException unused2) {
                        }
                    }
                    if (str2 != null && !str2.isEmpty()) {
                        String httpGet = httpGet("http://" + str2 + ":" + HTTP + "/?p=" + trim);
                        try {
                            datagramSocket.close();
                        } catch (Exception unused3) {
                        }
                        return httpGet;
                    }
                    try {
                        datagramSocket.close();
                    } catch (Exception unused4) {
                    }
                    return null;
                } catch (Throwable th) {
                    th = th;
                    datagramSocket2 = datagramSocket;
                    if (datagramSocket2 != null) {
                        try {
                            datagramSocket2.close();
                        } catch (Exception unused5) {
                        }
                    }
                    throw th;
                }
            } catch (Exception unused6) {
                if (datagramSocket != null) {
                    try {
                        datagramSocket.close();
                    } catch (Exception unused7) {
                    }
                }
                return null;
            }
        } catch (Exception unused8) {
            return null;
        } catch (Throwable th2) {
            try {
                if (datagramSocket2 != null) datagramSocket2.close();
            } catch (Exception ignored) {}
            return null;
        }
    }

    static String pack(String str, String str2, String str3, String str4, String str5) {
        try {
            JSONObject jSONObject = new JSONObject();
            if (str == null) {
                str = "";
            }
            jSONObject.put("name", str);
            if (str2 == null) {
                str2 = "";
            }
            jSONObject.put("url", str2);
            if (str3 == null) {
                str3 = "";
            }
            jSONObject.put("user", str3);
            if (str4 == null) {
                str4 = "";
            }
            jSONObject.put("pass", str4);
            if (str5 == null) {
                str5 = "";
            }
            jSONObject.put("epg", str5);
            return jSONObject.toString();
        } catch (Exception unused) {
            return "{}";
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void httpLoop() {
        ServerSocket serverSocket;
        byte[] bytes;
        String str;
        try {
            try {
                try {
                    ServerSocket serverSocket2 = new ServerSocket();
                    server = serverSocket2;
                    serverSocket2.setReuseAddress(true);
                    server.bind(new InetSocketAddress(HTTP));
                    server.setSoTimeout(1000);
                    while (running) {
                        try {
                            Socket accept = server.accept();
                            try {
                                accept.setSoTimeout(3000);
                                byte[] bArr = new byte[1024];
                                int read = accept.getInputStream().read(bArr);
                                String query = query(read > 0 ? new String(bArr, 0, read, StandardCharsets.UTF_8) : "", "p");
                                if (pin != null && pin.equals(query) && payload != null) {
                                    bytes = payload.getBytes(StandardCharsets.UTF_8);
                                    str = "HTTP/1.1 200 OK\r\n";
                                } else {
                                    bytes = "no".getBytes(StandardCharsets.UTF_8);
                                    str = "HTTP/1.1 404 Not Found\r\n";
                                }
                                OutputStream outputStream = accept.getOutputStream();
                                outputStream.write((str + "Content-Type: application/json; charset=utf-8\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                                outputStream.write(bytes);
                                outputStream.flush();
                            } catch (Exception unused) {
                            } catch (Throwable th) {
                                try {
                                    accept.close();
                                } catch (Exception unused2) {
                                }
                                throw th;
                            }
                            accept.close();
                        } catch (Exception unused3) {
                        }
                    }
                    serverSocket = server;
                    if (serverSocket == null) {
                        return;
                    }
                } catch (Exception unused4) {
                    serverSocket = server;
                    if (serverSocket == null) {
                        return;
                    }
                }
                serverSocket.close();
            } catch (Throwable th2) {
                try {
                    ServerSocket serverSocket3 = server;
                    if (serverSocket3 != null) {
                        serverSocket3.close();
                    }
                } catch (Exception unused5) {
                }
                throw th2;
            }
        } catch (Exception unused6) {
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void udpLoop() {
        DatagramSocket datagramSocket;
        try {
            try {
                try {
                    DatagramSocket datagramSocket2 = new DatagramSocket((SocketAddress) null);
                    udp = datagramSocket2;
                    datagramSocket2.setReuseAddress(true);
                    udp.setBroadcast(true);
                    udp.bind(new InetSocketAddress(UDP));
                    udp.setSoTimeout(800);
                    byte[] bArr = new byte[256];
                    String lanIp = lanIp();
                    while (running) {
                        try {
                            byte[] bytes = ("S2OFFER " + pin + " " + lanIp + " " + HTTP).getBytes(StandardCharsets.UTF_8);
                            udp.send(new DatagramPacket(bytes, bytes.length, InetAddress.getByName("255.255.255.255"), UDP));
                        } catch (Exception unused) {
                        }
                        try {
                            DatagramPacket datagramPacket = new DatagramPacket(bArr, 256);
                            udp.receive(datagramPacket);
                            String trim = new String(datagramPacket.getData(), 0, datagramPacket.getLength(), StandardCharsets.UTF_8).trim();
                            if (trim.startsWith("S2WANT ") && pin != null && trim.substring(7).trim().equals(pin)) {
                                byte[] bytes2 = ("S2OFFER " + pin + " " + lanIp + " " + HTTP).getBytes(StandardCharsets.UTF_8);
                                udp.send(new DatagramPacket(bytes2, bytes2.length, datagramPacket.getAddress(), datagramPacket.getPort()));
                            }
                        } catch (SocketTimeoutException unused2) {
                        }
                    }
                    datagramSocket = udp;
                    if (datagramSocket == null) {
                        return;
                    }
                } catch (Throwable th) {
                    try {
                        DatagramSocket datagramSocket3 = udp;
                        if (datagramSocket3 != null) {
                            datagramSocket3.close();
                        }
                    } catch (Exception unused3) {
                    }
                    throw th;
                }
            } catch (Exception unused4) {
                datagramSocket = udp;
                if (datagramSocket == null) {
                    return;
                }
            }
            datagramSocket.close();
        } catch (Exception unused5) {
        }
    }

    private static String query(String str, String str2) {
        int indexOf = str.indexOf("?");
        if (indexOf < 0) {
            return "";
        }
        int indexOf2 = str.indexOf(32, indexOf);
        for (String str3 : (indexOf2 > indexOf ? str.substring(indexOf + 1, indexOf2) : str.substring(indexOf + 1)).split("&")) {
            int indexOf3 = str3.indexOf(61);
            if (indexOf3 > 0 && str2.equals(str3.substring(0, indexOf3))) {
                return str3.substring(indexOf3 + 1).trim();
            }
        }
        return "";
    }

    private static String httpGet(String str) {
        try {
            HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(str).openConnection();
            httpURLConnection.setConnectTimeout(4000);
            httpURLConnection.setReadTimeout(6001);
            httpURLConnection.setRequestProperty(HttpHeaders.USER_AGENT, "Streamy2");
            if (httpURLConnection.getResponseCode() != 200) {
                return null;
            }
            InputStream inputStream = httpURLConnection.getInputStream();
            StringBuilder sb = new StringBuilder();
            byte[] bArr = new byte[2048];
            while (true) {
                int read = inputStream.read(bArr);
                if (read <= 0) {
                    inputStream.close();
                    httpURLConnection.disconnect();
                    return sb.toString();
                }
                sb.append(new String(bArr, 0, read, StandardCharsets.UTF_8));
            }
        } catch (Exception unused) {
            return null;
        }
    }

    static String lanIp() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface nextElement = networkInterfaces.nextElement();
                if (nextElement.isUp() && !nextElement.isLoopback()) {
                    Enumeration<InetAddress> inetAddresses = nextElement.getInetAddresses();
                    while (inetAddresses.hasMoreElements()) {
                        InetAddress nextElement2 = inetAddresses.nextElement();
                        if ((nextElement2 instanceof Inet4Address) && !nextElement2.isLoopbackAddress()) {
                            return nextElement2.getHostAddress();
                        }
                    }
                }
            }
            return "0.0.0.0";
        } catch (Exception unused) {
            return "0.0.0.0";
        }
    }
}
