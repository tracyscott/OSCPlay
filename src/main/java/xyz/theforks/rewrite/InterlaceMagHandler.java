package xyz.theforks.rewrite;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.math3.analysis.interpolation.SplineInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import com.illposed.osc.OSCMessage;

import xyz.theforks.CalibrationViewer;

public class InterlaceMagHandler implements RewriteHandler {
    private List<double[]> calibrationData;
    private PolynomialSplineFunction splineX;
    private PolynomialSplineFunction splineY;
    private PolynomialSplineFunction splineZ;
    private double curveLength;
    private double minX = Double.MAX_VALUE;
    private double maxX = Double.MIN_VALUE;
    private double minY = Double.MAX_VALUE;
    private double maxY = Double.MIN_VALUE;
    private int magNum;
    
    public InterlaceMagHandler() {
        calibrationData = new ArrayList<>();
    }

    public void initialize() throws IOException {
        // Nothing to do
        calibrationData.clear();
        loadCalibrationData("calibration" + magNum + ".csv");
        calculateBounds();
        fitCurve();
    }

    public String label() {
        return "Interlace Magnometer";
    }

    // Single line description of this handler, appropriate for a status bar display
    public String getHelp() {
        return "Interlace Magnometer " + magNum + " calibration points: " + calibrationData.size();
    }

    public int getNumArgs() {
        return 1;
    }

    @Override
    public String[] getArgNames() {
        return new String[]{"Magnometer Number"};
    }

    public boolean configure(String[] args) {
        if (args.length != 1) {
            return false;
        }
        try {
            int magNum = Integer.parseInt(args[0]);
            if (magNum < 1 || magNum > 3) {
                return false;
            }
            this.magNum = magNum;
            initialize();
            return true;
        } catch (NumberFormatException e) {
            System.err.println("Invalid argument: " + args[0]);
            return false;
        } catch (IOException e) {
            System.err.println("Error loading calibration data: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String[] getArgs() {
        return new String[]{String.valueOf(magNum)};
    }

    private void loadCalibrationData(String filename) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                if (lineNum++ == 0) {
                    continue; // Skip header
                }
                String[] values = line.split(",");
                if (values.length >= 4) { // timestamp,magx,magy,magz
                    double[] data = new double[3];
                    data[0] = Double.parseDouble(values[1]); // magx
                    data[1] = Double.parseDouble(values[2]); // magy
                    data[2] = Double.parseDouble(values[3]); // magz
                    calibrationData.add(data);
                }
                lineNum++;
            }
        }
        System.out.println("Loaded " + calibrationData.size() + " calibration points");
    }

    private void calculateBounds() {
        for (double[] point : calibrationData) {
            minX = Math.min(minX, point[0]);
            maxX = Math.max(maxX, point[0]);
            minY = Math.min(minY, point[1]);
            maxY = Math.max(maxY, point[1]);
        }
    }

    private void fitCurve() {
        int n = calibrationData.size();
        double[] t = new double[n];
        double[] x = new double[n];
        double[] y = new double[n];
        double[] z = new double[n];
        
        // Generate parameter values
        for (int i = 0; i < n; i++) {
            t[i] = i / (double)(n-1);
            x[i] = calibrationData.get(i)[0];
            y[i] = calibrationData.get(i)[1];
            z[i] = calibrationData.get(i)[2];
        }
        
        SplineInterpolator interpolator = new SplineInterpolator();
        splineX = interpolator.interpolate(t, x);
        splineY = interpolator.interpolate(t, y);
        splineZ = interpolator.interpolate(t, z);
        
        // Calculate approximate curve length
        curveLength = 0;
        double[] prevPoint = new double[]{splineX.value(0), splineY.value(0), splineZ.value(0)};
        for (double tt = 0.01; tt <= 1.0; tt += 0.01) {
            double[] point = new double[]{splineX.value(tt), splineY.value(tt), splineZ.value(tt)};
            curveLength += distance(prevPoint, point);
            prevPoint = point;
        }
    }
    
    private double[] findClosestPoint(double x, double y, double z) {
        double minDist = Double.MAX_VALUE;
        double bestT = 0;
        double[] inputPoint = new double[]{x, y, z};
        
        // Binary search for closest point
        for (double t = 0; t <= 1.0; t += 0.01) {
            double[] curvePoint = new double[]{
                splineX.value(t),
                splineY.value(t),
                splineZ.value(t)
            };
            double dist = distance(inputPoint, curvePoint);
            if (dist < minDist) {
                minDist = dist;
                bestT = t;
            }
        }
        
        // Refine search around best t
        double delta = 0.01;
        for (double t = Math.max(0, bestT-delta); t <= Math.min(1, bestT+delta); t += 0.001) {
            double[] curvePoint = new double[]{
                splineX.value(t),
                splineY.value(t),
                splineZ.value(t)
            };
            double dist = distance(inputPoint, curvePoint);
            if (dist < minDist) {
                minDist = dist;
                bestT = t;
            }
        }
        
        return new double[]{
            splineX.value(bestT),
            splineY.value(bestT),
            splineZ.value(bestT),
            bestT
        };
    }
    
    private double distance(double[] p1, double[] p2) {
        double dx = p1[0] - p2[0];
        double dy = p1[1] - p2[1];
        double dz = p1[2] - p2[2];
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    @Override
    public String getAddressPattern() {
        // This is an OSC Address, not a regex pattern, so the matching is defined as of:
        // https://opensoundcontrol.stanford.edu/spec-1_0.html
        // Also supports 1.1 XPath-style wildcards
        // https://opensoundcontrol.stanford.edu/files/2009-NIME-OSC-1.1.pdf
        //return "/lx/modulation/Mag[123]/mag";
        return "/lx/modulation/Mag" + magNum + "/mag";
    }

    @Override
    public OSCMessage process(OSCMessage message) {
        Object[] arguments = message.getArguments().toArray();
        if (arguments.length == 3) {
            double x = ((Number)arguments[0]).doubleValue();
            double y = ((Number)arguments[1]).doubleValue();
            double z = ((Number)arguments[2]).doubleValue();
            
            double[] closest = findClosestPoint(x, y, z);
            float t = (float)closest[3];  // Normalized position along curve
            
            arguments[0] = t;
            // System.out.println("Interlacing Mag" + magNum + " at t=" + t);
            String newAddress = message.getAddress();
            //String newAddress = message.getAddress().replace("Mag", "Angle").replace("mag", "angle");
            return new OSCMessage(newAddress, Arrays.asList(arguments[0]));
        }
        return message;
    }

    public List<double[]> getCalibrationData() {
        return calibrationData;
    }

    public PolynomialSplineFunction getSplineX() {
        return splineX;
    }

    public PolynomialSplineFunction getSplineY() {
        return splineY;
    }

    public PolynomialSplineFunction getSplineZ() {
        return splineZ;
    }

    @Override
    public void showPreferences() {
        new CalibrationViewer().show(this, "Calibration " + magNum);
    }
}