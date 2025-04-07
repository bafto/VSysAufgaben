package aqua.blatt1.client;

import javax.swing.*;
import java.awt.event.ActionListener;

public class SnapshotCollectorController implements ActionListener {
    private TankModel tankModel;
    private JFrame frame;

    public SnapshotCollectorController(TankModel tankModel, JFrame frame) {
        this.tankModel = tankModel;
    }

    @Override
    public void actionPerformed(java.awt.event.ActionEvent e) {
        tankModel.collectSnapshot((Integer s) -> {
            JOptionPane.showMessageDialog(frame, "snapshot collected: " + s);
            return null;
        });
    }
}
