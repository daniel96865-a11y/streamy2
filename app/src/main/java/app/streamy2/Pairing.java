package app.streamy2;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.json.JSONObject;

/** Local discovery carries no PIN; credentials only leave after authenticated key confirmation. */
final class Pairing {
    static final int TCP = 38473;
    static final int UDP = 38472;
    private static volatile Host host;

    static synchronized String startHost(String payload) {
        stop();
        try {
            Host next = new Host(payload);
            host = next;
            next.start();
            return next.pin;
        } catch (Exception e) { return null; }
    }

    static synchronized void stop() {
        Host old = host;
        host = null;
        if (old != null) old.close();
    }

    static boolean hosting() { Host current = host; return current != null && current.alive(); }

    private static final class Host {
        final ServerSocket server;
        final DatagramSocket discovery;
        final String pin = PairingCrypto.newPin();
        final long expires = System.nanoTime() + 120000000000L;
        volatile String payload;
        volatile Socket client;
        volatile boolean closed;
        int attempts;
        Host(String value) throws Exception {
            payload = value;
            server = new ServerSocket();
            try {
                server.setReuseAddress(true);
                server.bind(new InetSocketAddress(TCP));
                server.setSoTimeout(500);
                discovery = new DatagramSocket(UDP);
                discovery.setSoTimeout(500);
            } catch (Exception e) { server.close(); throw e; }
        }
        boolean alive() { return !closed && System.nanoTime() < expires; }
        void close() {
            closed = true; payload = null;
            try { server.close(); } catch (Exception ignored) { Quiet.ignored("Pairing", ignored); }
            discovery.close();
            try { if (client != null) client.close(); } catch (Exception ignored) { Quiet.ignored("Pairing", ignored); }
        }
        void start() {
            Thread tcp = new Thread(this::serve, "s2-pair-secure"); tcp.setDaemon(true); tcp.start();
            Thread udp = new Thread(this::discover, "s2-pair-discovery"); udp.setDaemon(true); udp.start();
        }
        void discover() {
            try {
                while (alive()) {
                    DatagramPacket packet = new DatagramPacket(new byte[128], 128);
                    try {
                        discovery.receive(packet);
                        String request = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.US_ASCII);
                        if (!request.matches("S2DISCOVER2 [0-9a-f-]{36}")) continue;
                        byte[] reply = request.replace("S2DISCOVER2", "S2OFFER2").getBytes(StandardCharsets.US_ASCII);
                        discovery.send(new DatagramPacket(reply, reply.length, packet.getAddress(), packet.getPort()));
                    } catch (SocketTimeoutException ignored) { Quiet.ignored("Pairing", ignored); }
                }
            } catch (Exception ignored) { Quiet.ignored("Pairing", ignored); }
            finally { close(); }
        }
        void serve() {
            try {
                while (alive() && attempts < 5) {
                    try (Socket socket = server.accept()) {
                        client = socket;
                        attempts++;
                        socket.setSoTimeout(10000);
                        DataInputStream in = new DataInputStream(socket.getInputStream());
                        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                        try (PairingCrypto.Session session = PairingCrypto.exchange(in, out, pin, true)) {
                            String value = payload;
                            if (!alive() || value == null) break;
                            PairingCrypto.writeBytes(out, session.encrypt(value.getBytes(StandardCharsets.UTF_8)));
                            break; // One successful transfer consumes this PIN.
                        }
                    } catch (SocketTimeoutException ignored) { Quiet.ignored("Pairing", ignored); }
                    catch (Exception ignored) { /* No credential response after failed authentication. */ Quiet.ignored("Pairing", ignored); }
                    finally { client = null; }
                }
            } finally { close(); }
        }
    }

    static String fetch(String pin, int timeoutMs) {
        if (pin == null || !pin.matches("[0-9]{6}")) return null;
        long deadline = System.nanoTime() + Math.max(4000, timeoutMs) * 1000000L;
        String nonce = UUID.randomUUID().toString();
        byte[] query = ("S2DISCOVER2 " + nonce).getBytes(StandardCharsets.US_ASCII);
        Set<InetAddress> tried = new HashSet<>();
        try (DatagramSocket udp = new DatagramSocket()) {
            udp.setBroadcast(true); udp.setSoTimeout(500);
            while (System.nanoTime() < deadline && tried.size() < 8) {
                udp.send(new DatagramPacket(query, query.length, InetAddress.getByName("255.255.255.255"), UDP));
                DatagramPacket reply = new DatagramPacket(new byte[128], 128);
                try { udp.receive(reply); } catch (SocketTimeoutException e) { continue; }
                String response = new String(reply.getData(), 0, reply.getLength(), StandardCharsets.US_ASCII);
                if (!response.equals("S2OFFER2 " + nonce) || !tried.add(reply.getAddress())) continue;
                try (Socket socket = new Socket()) {
                    int remaining = (int) Math.min(10000, Math.max(1, (deadline - System.nanoTime()) / 1000000));
                    socket.connect(new InetSocketAddress(reply.getAddress(), TCP), Math.min(3000, remaining));
                    socket.setSoTimeout(remaining);
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    try (PairingCrypto.Session session = PairingCrypto.exchange(in, out, pin, false)) {
                        byte[] encrypted = PairingCrypto.readBytes(in, PairingCrypto.MAX_PAYLOAD + 28);
                        return new String(session.decrypt(encrypted), StandardCharsets.UTF_8);
                    }
                } catch (Exception ignored) { Quiet.ignored("Pairing", ignored); }
            }
        } catch (Exception ignored) { Quiet.ignored("Pairing", ignored); }
        return null;
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
