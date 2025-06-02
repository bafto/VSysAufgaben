package aqua.blatt1.broker;

import java.net.InetSocketAddress;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.NoSuchPaddingException;
import javax.swing.JOptionPane;

import aqua.blatt1.common.SecureEndpoint;
import messaging.Endpoint;
import aqua.blatt1.common.Properties;

public class Poisoner {
	private final Endpoint endpoint;
	private final InetSocketAddress broker;

	public Poisoner() throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, InvalidAlgorithmParameterException {
		this.endpoint = new SecureEndpoint(new Endpoint());
		this.broker = new InetSocketAddress(Properties.HOST, Properties.PORT);
	}

	public void sendPoison() {
		endpoint.send(broker, new PoisonPill());
	}

	public static void main(String[] args) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, InvalidAlgorithmParameterException {
		JOptionPane.showMessageDialog(null, "Press OK button to poison server.");
		new Poisoner().sendPoison();
	}
}
