package xyz.theforks.calibration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class LinearModelTest {

    private static LinearModel fit(double[][] rawAndLabel) {
        List<LabeledPoint> points = new ArrayList<>();
        for (double[] p : rawAndLabel) {
            points.add(new LabeledPoint(new double[]{p[0]}, p[1]));
        }
        LinearModel model = new LinearModel();
        model.fit(new ArrayList<>(), points);
        return model;
    }

    @Test
    void testInterpolatesBetweenPoints() {
        LinearModel model = fit(new double[][]{{0, 0}, {100, 10}, {300, 20}});

        assertEquals(5, model.map(new double[]{50}), 1e-9);
        assertEquals(15, model.map(new double[]{200}), 1e-9);
        assertEquals(1, model.getDimensions());
    }

    @Test
    void testPointsInAnyOrder() {
        LinearModel model = fit(new double[][]{{300, 20}, {0, 0}, {100, 10}});
        assertEquals(15, model.map(new double[]{200}), 1e-9);
    }

    @Test
    void testDecreasingMapping() {
        // Reading falls as the physical value rises
        LinearModel model = fit(new double[][]{{1000, 0}, {0, 90}});
        assertEquals(45, model.map(new double[]{500}), 1e-9);
    }

    @Test
    void testClampsOutsideRange() {
        LinearModel model = fit(new double[][]{{0, 0}, {100, 10}});
        assertEquals(0, model.map(new double[]{-50}), 1e-9);
        assertEquals(10, model.map(new double[]{150}), 1e-9);
    }

    @Test
    void testRejectsBadInput() {
        assertThrows(IllegalArgumentException.class, () -> fit(new double[][]{{0, 0}}));
        assertThrows(IllegalArgumentException.class, () -> fit(new double[][]{{5, 0}, {5, 10}}));

        LinearModel model = new LinearModel();
        List<LabeledPoint> multiDim = Arrays.asList(
            new LabeledPoint(new double[]{0, 0}, 0), new LabeledPoint(new double[]{1, 1}, 1));
        assertThrows(IllegalArgumentException.class, () -> model.fit(new ArrayList<>(), multiDim));
    }
}
