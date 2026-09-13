package app.streamy2;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.*;
import static org.junit.Assert.*;

public class PairingServerTest {
    @After public void stop() { Pairing.stop(); }

    @Test public void discoveryContainsNoPinAndHostServesOnlyOneAuthenticatedTransfer() throws Exception {
        String payload="{\"user\":\"test\",\"pass\":\"private-password\"}";
        String pin=Pairing.startHost(payload); assertNotNull(pin);
        String nonce=UUID.randomUUID().toString();
        try(DatagramSocket discovery=new DatagramSocket()) {
            discovery.setSoTimeout(3000);
            byte[] query=("S2DISCOVER2 "+nonce).getBytes(StandardCharsets.US_ASCII);
            discovery.send(new DatagramPacket(query,query.length,InetAddress.getLoopbackAddress(),Pairing.UDP));
            DatagramPacket reply=new DatagramPacket(new byte[128],128); discovery.receive(reply);
            assertEquals("S2OFFER2 "+nonce,new String(reply.getData(),0,reply.getLength(),StandardCharsets.US_ASCII));
        }
        try(Socket socket=new Socket(InetAddress.getLoopbackAddress(),Pairing.TCP)) {
            socket.setSoTimeout(5000);
            DataInputStream in=new DataInputStream(socket.getInputStream());
            try(PairingCrypto.Session session=PairingCrypto.exchange(in,new DataOutputStream(socket.getOutputStream()),pin,false)) {
                assertEquals(payload,new String(session.decrypt(PairingCrypto.readBytes(in,PairingCrypto.MAX_PAYLOAD+28)),StandardCharsets.UTF_8));
            }
        }
        for(int i=0;i<20 && Pairing.hosting();i++) Thread.sleep(10);
        assertFalse(Pairing.hosting());
    }

    @Test public void fiveWrongPinsCloseTheHostWithoutReturningCredentials() throws Exception {
        String pin=Pairing.startHost("{\"password\":\"secret\"}"); assertNotNull(pin);
        String wrong=pin.equals("000000")?"111111":"000000";
        for(int i=0;i<5;i++) {
            try(Socket socket=new Socket(InetAddress.getLoopbackAddress(),Pairing.TCP)) {
                socket.setSoTimeout(5000);
                assertThrows(Exception.class,()->PairingCrypto.exchange(new DataInputStream(socket.getInputStream()),new DataOutputStream(socket.getOutputStream()),wrong,false));
            }
        }
        for(int i=0;i<20 && Pairing.hosting();i++) Thread.sleep(10);
        assertFalse(Pairing.hosting());
    }
}
