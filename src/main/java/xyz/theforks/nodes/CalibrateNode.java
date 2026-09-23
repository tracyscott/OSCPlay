package xyz.theforks.nodes;

import java.io.IOException;
import java.util.Arrays;

import com.illposed.osc.OSCMessage;

import javafx.scene.control.Alert;
import xyz.theforks.CalibrationViewer;
import xyz.theforks.calibration.Calibration;
import xyz.theforks.calibration.CalibrationModel;
import xyz.theforks.calibration.CalibrationStore;
import xyz.theforks.calibration.SampleExtractor;
import xyz.theforks.service.ProjectManager;

/**
 * Replaces sensor readings with calibrated values using a calibration saved in the
 * project's Calibrations/ directory. Messages whose leading arguments form a reading
 * (one numeric argument per calibration dimension) become a single float on the same
 * address; anything else passes through unchanged.
 */
public class CalibrateNode implements OSCNode {
    private static ProjectManager projectManager;
    private static CalibrationStore storeOverride;

    private volatile String addressPattern;
    private volatile String calibrationName;
    private volatile Calibration calibration;
    // Swapped in one step when a calibration is reloaded while messages are being processed
    private volatile CalibrationModel model;

    /**
     * Set the project manager used to find the current project's calibrations.
     */
    public static void setProjectManager(ProjectManager pm) {
        projectManager = pm;
    }

    /**
     * Use a fixed calibration store instead of the current project's (for tests and tools).
     * Pass null to go back to the project's store.
     */
    public static void setCalibrationStore(CalibrationStore store) {
        storeOverride = store;
    }

    /**
     * The store for the current project's calibrations, or null if no project is open.
     */
    protected static CalibrationStore calibrationStore() {
        if (storeOverride != null) {
            return storeOverride;
        }
        if (projectManager != null && projectManager.hasOpenProject()) {
            return new CalibrationStore(projectManager.getCalibrationsDir());
        }
        return null;
    }

    @Override
    public String getAddressPattern() {
        return addressPattern;
    }

    @Override
    public String label() {
        return "Calibrate";
    }

    @Override
    public String getHelp() {
        if (calibration == null) {
            return "Replaces sensor readings with values from a saved calibration";
        }
        return "Calibrate " + addressPattern + " with " + calibrationName + " ("
            + calibration.getModel() + ", " + calibration.getPoints().size() + " labeled points)";
    }

    @Override
    public int getNumArgs() {
        return 2;
    }

    @Override
    public String[] getArgNames() {
        return new String[]{"Address Pattern", "Calibration"};
    }

    @Override
    public String[] getArgs() {
        return new String[]{addressPattern, calibrationName};
    }

    @Override
    public boolean configure(String[] args) {
        if (args.length != 2) {
            return false;
        }
        CalibrationStore store = calibrationStore();
        if (store == null) {
            System.err.println("CalibrateNode: no project open to load calibration " + args[1] + " from");
            return false;
        }
        try {
            return useCalibration(args[0], store.load(args[1]));
        } catch (IOException e) {
            System.err.println("CalibrateNode: " + e.getMessage());
            return false;
        }
    }

    /**
     * Apply a calibration to messages matching the address pattern.
     *
     * @return false if the calibration can't be fit
     */
    protected boolean useCalibration(String addressPattern, Calibration calibration) {
        try {
            CalibrationModel fitted = calibration.getFittedModel();
            this.addressPattern = addressPattern;
            this.calibrationName = calibration.getName();
            this.calibration = calibration;
            this.model = fitted;
            return true;
        } catch (IllegalArgumentException e) {
            System.err.println("CalibrateNode: can't use calibration " + calibration.getName() + ": " + e.getMessage());
            return false;
        }
    }

    public Calibration getCalibration() {
        return calibration;
    }

    public String getCalibrationName() {
        return calibrationName;
    }

    /**
     * Re-load a calibration in every node that uses it, e.g. after it has been re-recorded.
     *
     * @return Number of nodes reloaded
     */
    public static int reloadCalibration(Iterable<OSCNode> nodes, String name) {
        int reloaded = 0;
        for (OSCNode node : nodes) {
            if (node instanceof CalibrateNode) {
                CalibrateNode calibrateNode = (CalibrateNode) node;
                if (name.equals(calibrateNode.getCalibrationName()) && calibrateNode.configure(calibrateNode.getArgs())) {
                    reloaded++;
                }
            }
        }
        return reloaded;
    }

    @Override
    public void process(java.util.List<xyz.theforks.model.MessageRequest> requests) {
        OSCMessage message = inputMessage(requests);
        CalibrationModel model = this.model;
        if (message == null || model == null || !message.getAddress().matches(addressPattern)) return;

        double[] values = SampleExtractor.toValues(message.getArguments().toArray(), model.getDimensions());
        if (values == null) return;

        float calibrated = (float) model.map(values);
        replaceMessage(requests, new OSCMessage(message.getAddress(), Arrays.asList(calibrated)));
    }

    @Override
    public void showPreferences() {
        if (calibration == null) {
            new Alert(Alert.AlertType.INFORMATION, "No calibration loaded.").showAndWait();
        } else if (calibration.getDimensions() == 3) {
            new CalibrationViewer().show(calibration, "Calibration " + calibrationName);
        } else {
            new Alert(Alert.AlertType.INFORMATION, getHelp() + "\n"
                + calibration.getSamples().size() + " samples from "
                + (calibration.getSourceRecording() != null ? calibration.getSourceRecording() : "unknown recording"))
                .showAndWait();
        }
    }
}
