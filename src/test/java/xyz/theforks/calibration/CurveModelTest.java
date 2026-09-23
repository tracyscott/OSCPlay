package xyz.theforks.calibration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class CurveModelTest {

    private static CurveModel fit(List<CalibrationSample> samples, List<LabeledPoint> points) {
        CurveModel model = new CurveModel();
        model.fit(samples, points);
        return model;
    }

    private static List<LabeledPoint> marks(List<Double> angles, boolean distort) {
        List<LabeledPoint> points = new ArrayList<>();
        for (double a : angles) {
            points.add(new LabeledPoint(SyntheticSensor.curve(a, distort), a));
        }
        return points;
    }

    @Test
    void testUnlabeledPositionFollowsArcLengthNotSampleIndex() {
        CurveModel model = fit(SyntheticSensor.samples(SyntheticSensor.sweepAngles(), false, 42), new ArrayList<>());

        // On an undistorted circle, arc length is proportional to angle. Uneven calibration
        // speed and the backtrack would skew an index-parameterized curve well past this.
        for (double deg = 0; deg <= 270; deg += 15) {
            assertEquals(deg / 270, model.map(SyntheticSensor.curve(deg, false)), 0.01, "at " + deg + " degrees");
        }
    }

    @Test
    void testLabelsCorrectDistortion() {
        List<Double> markAngles = CalibrationBuilder.labelRange(0, 270, 10);
        List<CalibrationSample> samples = SyntheticSensor.samples(
            SyntheticSensor.markToMarkAngles(markAngles, 2500, 700), true, 5);
        CurveModel labeled = fit(samples, marks(markAngles, true));
        CurveModel unlabeled = fit(samples, new ArrayList<>());

        double worstUnlabeled = 0;
        for (double deg = 0; deg <= 270; deg += 2.5) {
            double[] reading = SyntheticSensor.curve(deg, true);
            assertEquals(deg, labeled.map(reading), 1.0, "at " + deg + " degrees");
            worstUnlabeled = Math.max(worstUnlabeled, Math.abs(unlabeled.map(reading) * 270 - deg));
        }
        // Without labels, distance along the warped curve is well off the true angle
        assertTrue(worstUnlabeled > 5, "unlabeled error " + worstUnlabeled);
    }

    @Test
    void testLabelsCanDecreaseAlongCurve() {
        // Marks visited from 270 down to 0
        List<Double> markAngles = CalibrationBuilder.labelRange(270, 0, -30);
        List<CalibrationSample> samples = SyntheticSensor.samples(
            SyntheticSensor.markToMarkAngles(markAngles, 2000, 700), false, 6);
        CurveModel model = fit(samples, marks(markAngles, false));

        assertEquals(135, model.map(SyntheticSensor.curve(135, false)), 1.0);
    }

    @Test
    void testDistortedCurveIsMonotonicAndSpansFullRange() {
        CurveModel model = fit(SyntheticSensor.samples(SyntheticSensor.sweepAngles(), true, 42), new ArrayList<>());

        assertEquals(0.0, model.map(SyntheticSensor.curve(0, true)), 0.02);
        assertEquals(1.0, model.map(SyntheticSensor.curve(270, true)), 0.02);
        double previous = -1;
        for (double deg = 0; deg <= 270; deg += 5) {
            double t = model.map(SyntheticSensor.curve(deg, true));
            assertTrue(t > previous, "position should increase with angle at " + deg + " degrees");
            previous = t;
        }
    }

    @Test
    void testReadingsBeyondEndsClamp() {
        List<Double> markAngles = CalibrationBuilder.labelRange(0, 270, 30);
        List<CalibrationSample> samples = SyntheticSensor.samples(
            SyntheticSensor.markToMarkAngles(markAngles, 2000, 700), false, 7);
        CurveModel model = fit(samples, marks(markAngles, false));

        assertEquals(0, model.map(SyntheticSensor.curve(-20, false)), 0.5);
        assertEquals(270, model.map(SyntheticSensor.curve(290, false)), 0.5);
    }

    @Test
    void testReturnSweepKeepsFullCurve() {
        List<Double> there = SyntheticSensor.sweepAngles();
        List<Double> thereAndBack = new ArrayList<>(there);
        List<Double> back = new ArrayList<>(there);
        Collections.reverse(back);
        thereAndBack.addAll(back);
        CurveModel model = fit(SyntheticSensor.samples(thereAndBack, false, 8), new ArrayList<>());

        assertEquals(0.5, model.map(SyntheticSensor.curve(135, false)), 0.01);
        assertEquals(1.0, model.map(SyntheticSensor.curve(270, false)), 0.01);
    }

    @Test
    void testRejectsLabelsOutOfOrder() {
        List<CalibrationSample> samples = SyntheticSensor.samples(SyntheticSensor.sweepAngles(), false, 9);
        List<LabeledPoint> points = marks(List.of(0.0, 90.0, 180.0, 270.0), false);
        // Swap two labels, as if the marks were visited out of order
        points.set(1, new LabeledPoint(points.get(1).getValues(), 180));
        points.set(2, new LabeledPoint(points.get(2).getValues(), 90));

        assertThrows(IllegalArgumentException.class, () -> fit(samples, points));
    }

    @Test
    void testRejectsRecordingWithoutMovement() {
        List<CalibrationSample> samples = SyntheticSensor.samples(Collections.nCopies(100, 0.0), false, 10);
        assertThrows(IllegalArgumentException.class, () -> fit(samples, new ArrayList<>()));
    }
}
