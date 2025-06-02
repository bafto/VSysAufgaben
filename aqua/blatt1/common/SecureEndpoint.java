package aqua.blatt1.common;

import messaging.Endpoint;
import messaging.Message;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.InetSocketAddress;
import java.util.HexFormat;

public class SecureEndpoint extends Endpoint {
    final SecretKeySpec keySpec;
    private final Endpoint endpoint;
    private final Cipher encrypter;
    private final Cipher decrypter;

    public SecureEndpoint(Endpoint e) {
        try {
            keySpec = new SecretKeySpec("CAFEBABECAFEBABE".getBytes("utf-8"), "AES");

            this.endpoint = e;
            this.encrypter = Cipher.getInstance("AES/ECB/PKCS5Padding");
            this.decrypter = Cipher.getInstance("AES/ECB/PKCS5Padding");

            encrypter.init(Cipher.ENCRYPT_MODE, keySpec);
            decrypter.init(Cipher.DECRYPT_MODE, keySpec);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static byte[] getBytes(Serializable s) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(s);
            out.flush();
            return bos.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static Serializable getBytes(byte[] decrypted) {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(decrypted))) {
            return (Serializable) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Failed to deserialize message", e);
        }
    }

    @Override
    public void send(InetSocketAddress receiver, Serializable message) {
        byte[] bytes = getBytes(message);
        try {
            endpoint.send(receiver, encrypter.doFinal(bytes));
            encrypter.init(Cipher.ENCRYPT_MODE, keySpec);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send init cipher", e);
        }
    }

    @Override
    public Message blockingReceive() {
        Message m = endpoint.blockingReceive();

        try {
            final Message r =  new Message(getBytes(decrypter.doFinal((byte[])m.getPayload())), m.getSender());
            decrypter.init(Cipher.DECRYPT_MODE, keySpec);
            return r;
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to receive init cipher", e);
        }
    }

    @Override
    public Message nonBlockingReceive() {
        Message m = endpoint.nonBlockingReceive();
        if (m == null) {
            return null;
        }
        try {
            final Message r = new Message(getBytes(decrypter.doFinal((byte[])m.getPayload())), m.getSender());
            decrypter.init(Cipher.DECRYPT_MODE, keySpec);
            return r;
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to receive init cipher", e);
        }
    }
}
