package aqua.blatt1.client;

import java.awt.event.ActionListener;

public class SnapshotController implements ActionListener {
    private final TankModel tankModel;

    public SnapshotController(TankModel tankModel) {
        this.tankModel = tankModel;
    }

    @Override
    public void actionPerformed(java.awt.event.ActionEvent e) {
        tankModel.initiateSnapshot();
    }
}
