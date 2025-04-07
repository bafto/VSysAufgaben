package aqua.blatt1.client;

import javax.swing.*;
import java.awt.event.ActionListener;

public class SnapshotController implements ActionListener {
    private final TankModel tankModel;
    private final JFrame frame;

    public SnapshotController(TankModel tankModel, JFrame frame) {
        this.tankModel = tankModel;
        this.frame = frame;
    }

    @Override
    public void actionPerformed(java.awt.event.ActionEvent e) {
        tankModel.initiateSnapshot((Integer i) -> {
            JOptionPane.showMessageDialog(frame, "snapshot collected: " + i);
            return null;
        });
    }
}
