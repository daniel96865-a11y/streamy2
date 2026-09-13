package app.streamy2;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.agreement.jpake.*;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;

/** J-PAKE authenticates the six-digit PIN without sending it or an offline password verifier. */
final class PairingCrypto {
    static final int VERSION = 0x53325032;
    static final int MAX_PAYLOAD = 65536;
    private static final SecureRandom RANDOM = new SecureRandom();

    static String newPin() {
        return String.format(java.util.Locale.US, "%06d", RANDOM.nextInt(1000000));
    }

    static final class Session implements AutoCloseable {
        private final byte[] key;
        private final byte[] context;
        private Session(byte[] key, byte[] context) { this.key = key; this.context = context; }
        byte[] encrypt(byte[] plaintext) throws Exception {
            if (plaintext.length > MAX_PAYLOAD) throw new IOException("Playlist zu groß");
            byte[] nonce = new byte[12];
            RANDOM.nextBytes(nonce);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce);
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] result = Arrays.copyOf(nonce, nonce.length + ciphertext.length);
            System.arraycopy(ciphertext, 0, result, nonce.length, ciphertext.length);
            return result;
        }
        byte[] decrypt(byte[] ciphertext) throws Exception {
            if (ciphertext.length < 28 || ciphertext.length > MAX_PAYLOAD + 28) throw new IOException("Ungültige Playlist");
            return cipher(Cipher.DECRYPT_MODE, Arrays.copyOf(ciphertext, 12))
                    .doFinal(ciphertext, 12, ciphertext.length - 12);
        }
        private Cipher cipher(int mode, byte[] nonce) throws Exception {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(context);
            return cipher;
        }
        @Override public void close() { Arrays.fill(key, (byte) 0); }
    }

    static Session exchange(DataInputStream in, DataOutputStream out, String pin, boolean host) throws Exception {
        if (pin == null || !pin.matches("[0-9]{6}")) throw new IOException("Ungültige PIN");
        String id = (host ? "host:" : "client:") + UUID.randomUUID();
        char[] password = pin.toCharArray();
        JPAKEParticipant participant;
        try { participant = new JPAKEParticipant(id, password); }
        finally { Arrays.fill(password, '\0'); }
        out.writeInt(VERSION);
        JPAKERound1Payload first = participant.createRound1PayloadToSend();
        out.writeUTF(first.getParticipantId());
        writeNumber(out, first.getGx1()); writeNumber(out, first.getGx2());
        writeProof(out, first.getKnowledgeProofForX1()); writeProof(out, first.getKnowledgeProofForX2());
        out.flush();
        if (in.readInt() != VERSION) throw new IOException("Beide Geräte benötigen Streamy 3.29 oder neuer");
        String peer = readId(in);
        if (!peer.startsWith(host ? "client:" : "host:")) throw new IOException("Ungültige Gegenstelle");
        participant.validateRound1PayloadReceived(new JPAKERound1Payload(peer, readNumber(in), readNumber(in), readProof(in), readProof(in)));
        JPAKERound2Payload second = participant.createRound2PayloadToSend();
        out.writeUTF(id); writeNumber(out, second.getA()); writeProof(out, second.getKnowledgeProofForX2s()); out.flush();
        participant.validateRound2PayloadReceived(new JPAKERound2Payload(readId(in), readNumber(in), readProof(in)));
        BigInteger material = participant.calculateKeyingMaterial();
        JPAKERound3Payload third = participant.createRound3PayloadToSend(material);
        out.writeUTF(id); writeNumber(out, third.getMacTag()); out.flush();
        participant.validateRound3PayloadReceived(new JPAKERound3Payload(readId(in), readNumber(in)), material);
        byte[] context = ("Streamy2 pairing v2\n" + (host ? id + "\n" + peer : peer + "\n" + id)).getBytes(StandardCharsets.UTF_8);
        byte[] secret = material.toByteArray();
        byte[] key = new byte[32];
        try {
            HKDFBytesGenerator kdf = new HKDFBytesGenerator(new SHA256Digest());
            kdf.init(new HKDFParameters(secret, null, context));
            kdf.generateBytes(key, 0, key.length);
        } finally { Arrays.fill(secret, (byte) 0); }
        return new Session(key, context);
    }

    static void writeBytes(DataOutputStream out, byte[] bytes) throws IOException {
        out.writeInt(bytes.length); out.write(bytes); out.flush();
    }
    static byte[] readBytes(DataInputStream in, int max) throws IOException {
        int length = in.readInt();
        if (length < 1 || length > max) throw new IOException("Ungültige Nachrichtengröße");
        byte[] bytes = new byte[length]; in.readFully(bytes); return bytes;
    }
    private static void writeNumber(DataOutputStream out, BigInteger number) throws IOException { writeBytes(out, number.toByteArray()); }
    private static BigInteger readNumber(DataInputStream in) throws IOException { return new BigInteger(readBytes(in, 385)); }
    private static void writeProof(DataOutputStream out, BigInteger[] proof) throws IOException { writeNumber(out, proof[0]); writeNumber(out, proof[1]); }
    private static BigInteger[] readProof(DataInputStream in) throws IOException { return new BigInteger[]{readNumber(in), readNumber(in)}; }
    private static String readId(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        if (length < 1 || length > 80) throw new IOException("Ungültige Gerätekennung");
        byte[] bytes = new byte[length]; in.readFully(bytes);
        String id = new String(bytes, StandardCharsets.US_ASCII);
        if (!id.matches("(host|client):[0-9a-f-]{36}")) throw new IOException("Ungültige Gerätekennung");
        return id;
    }
}
