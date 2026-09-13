package app.streamy2;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class PairingCryptoTest {
    private PairingCrypto.Session[] pair(String hostPin, String clientPin) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Future<PairingCrypto.Session> host = executor.submit(() -> {
                try (Socket socket = listener.accept()) {
                    socket.setSoTimeout(5000);
                    return PairingCrypto.exchange(new DataInputStream(socket.getInputStream()), new DataOutputStream(socket.getOutputStream()), hostPin, true);
                }
            });
            Future<PairingCrypto.Session> client = executor.submit(() -> {
                try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort())) {
                    socket.setSoTimeout(5000);
                    return PairingCrypto.exchange(new DataInputStream(socket.getInputStream()), new DataOutputStream(socket.getOutputStream()), clientPin, false);
                }
            });
            return new PairingCrypto.Session[]{host.get(10, TimeUnit.SECONDS), client.get(10, TimeUnit.SECONDS)};
        } finally { executor.shutdownNow(); }
    }

    @Test public void authenticatedTransferAndTamperDetection() throws Exception {
        PairingCrypto.Session[] pair = pair("012345", "012345");
        try {
            byte[] plain = "{\"user\":\"daniel\",\"pass\":\"not-public\"}".getBytes(StandardCharsets.UTF_8);
            byte[] encrypted = pair[0].encrypt(plain);
            assertFalse(new String(encrypted, StandardCharsets.ISO_8859_1).contains("not-public"));
            assertArrayEquals(plain, pair[1].decrypt(encrypted));
            encrypted[encrypted.length - 1] ^= 1;
            assertThrows(Exception.class, () -> pair[1].decrypt(encrypted));
        } finally { for (PairingCrypto.Session session : pair) session.close(); }
    }

    @Test public void wrongPinNeverConfirmsSession() {
        assertThrows(ExecutionException.class, () -> pair("123456", "654321"));
    }

    @Test public void capturedPayloadCannotBeUsedInAnotherSession() throws Exception {
        PairingCrypto.Session[] first = pair("123456", "123456");
        PairingCrypto.Session[] second = pair("123456", "123456");
        try {
            byte[] captured = first[0].encrypt("playlist".getBytes(StandardCharsets.UTF_8));
            assertThrows(Exception.class, () -> second[1].decrypt(captured));
        } finally {
            for (PairingCrypto.Session session : first) session.close();
            for (PairingCrypto.Session session : second) session.close();
        }
    }

    @Test public void oversizedFrameIsRejectedBeforeAllocation() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new DataOutputStream(bytes).writeInt(Integer.MAX_VALUE);
        assertThrows(IOException.class, () -> PairingCrypto.readBytes(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), 1024));
    }

    @Test public void pinAlwaysHasSixDigitsIncludingLeadingZeros() {
        for (int i = 0; i < 1000; i++) assertTrue(PairingCrypto.newPin().matches("[0-9]{6}"));
    }
}
