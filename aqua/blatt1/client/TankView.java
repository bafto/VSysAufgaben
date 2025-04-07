package aqua.blatt1.client;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Observable;
import java.util.Observer;

import javax.swing.*;

import aqua.blatt1.common.FishModel;
import aqua.blatt1.common.msgtypes.SnapshotToken;

@SuppressWarnings("serial")
public class TankView extends JPanel implements Observer {
	private final TankModel tankModel;
	private final FishView fishView;
	private final Runnable repaintRunnable;

	public TankView(final TankModel tankModel) {
		this.tankModel = tankModel;
		fishView = new FishView();

		repaintRunnable = new Runnable() {
			@Override
			public void run() {
				repaint();
			}
		};

		setPreferredSize(new Dimension(TankModel.WIDTH, TankModel.HEIGHT));
		setBackground(new Color(175, 200, 235));

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				tankModel.newFish(e.getX(), e.getY());
			}
		});
	}

	@SuppressWarnings("unused")
	private void drawBorders(Graphics2D g2d) {
		g2d.drawLine(1, 1, 1, TankModel.HEIGHT);
		g2d.drawLine(TankModel.WIDTH - 2, 0, TankModel.WIDTH - 2, TankModel.HEIGHT);
	}

	private boolean showing = false;
	private void doDrawing(Graphics g) {
		Graphics2D g2d = (Graphics2D) g;

		for (FishModel fishModel : tankModel) {
			g2d.drawImage(fishView.getImage(fishModel), fishModel.getX(), fishModel.getY(), null);
			g2d.drawString(fishModel.getId(), fishModel.getX(), fishModel.getY());
		}

		if (!tankModel.hasToken()) {
			drawBorders(g2d);
		}

		final SnapshotToken snapshotToken = tankModel.getSnapshotToken();
		if (snapshotToken != null && !showing) {
			System.out.println("Snapshot Token: " + snapshotToken.getState());
			showing = true;
			final String msg = String.format("%d Snapshot Token", snapshotToken.getState());
			JOptionPane.showMessageDialog(this, msg, msg, JOptionPane.INFORMATION_MESSAGE);
			System.out.println("Snapshot Token2: " + snapshotToken.getState());
		}
	}

	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		doDrawing(g);
	}

	@Override
	public void update(Observable o, Object arg) {
		SwingUtilities.invokeLater(repaintRunnable);
	}
}
