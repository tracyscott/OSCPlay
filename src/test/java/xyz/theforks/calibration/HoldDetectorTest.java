package xyz.theforks.calibration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class HoldDetectorTest {

    @Test
    void testEstimateNoise() {
        // 3 axes of independent noise combine to sqrt(3) * sigma
        double expected = SyntheticSensor.NOISE * Math.sqrt(3);

        List<CalibrationSample> still = SyntheticSensor.samples(Collections.nCopies(500, 45.0), false, 1);
        assertEquals(expected, HoldDetector.estimateNoise(still), expected * 0.1);

        // Movement between marks biases the estimate upward, but it only needs to be the right
        // order of magnitude to set the stillness threshold
        List<CalibrationSample> marks = SyntheticSensor.samples(
            SyntheticSensor.markToMarkAngles(Arrays.asList(0.0, 90.0), 3000, 800), false, 1);
        assertEquals(expected, HoldDetector.estimateNoise(marks), expected * 0.5);
    }

    @Test
    void testFindsEachMark() {
        List<Double> marks = CalibrationBuilder.labelRange(0, 270, 10);
        List<CalibrationSample> samples = SyntheticSensor.samples(
            SyntheticSensor.markToMarkAngles(marks, 2500, 700), true, 2);

        List<HoldDetector.Hold> holds = HoldDetector.forSamples(samples, 1000).detect(samples);

        assertEquals(marks.size(), holds.size());
        for (int i = 0; i < marks.size(); i++) {
            double[] expected = SyntheticSensor.curve(marks.get(i), true);
            assertTrue(HoldDetector.distance(expected, holds.get(i).getMean()) < SyntheticSensor.NOISE,
                "hold " + i + " mean should be close to the mark's reading");
        }
    }

    @Test
    void testIgnoresPausesShorterThanMinimum() {
        // Brief 400ms pauses at each mark
        List<Double> shortPause = SyntheticSensor.markToMarkAngles(Arrays.asList(0.0, 45.0, 90.0), 400, 700);
        List<CalibrationSample> samples = SyntheticSensor.samples(shortPause, false, 3);
        assertEquals(0, HoldDetector.forSamples(samples, 1000).detect(samples).size());

        List<Double> longPause = SyntheticSensor.markToMarkAngles(Arrays.asList(0.0, 45.0, 90.0), 2000, 700);
        List<CalibrationSample> longPauses = SyntheticSensor.samples(longPause, false, 3);
        assertEquals(3, HoldDetector.forSamples(longPauses, 1000).detect(longPauses).size());
    }

    @Test
    void testExplicitThreshold() {
        List<CalibrationSample> samples = SyntheticSensor.samples(
            SyntheticSensor.markToMarkAngles(Arrays.asList(0.0, 90.0), 2000, 700), false, 4);

        // A threshold far below the noise finds nothing still
        assertEquals(0, new HoldDetector(1, 1000).detect(samples).size());
        assertEquals(2, new HoldDetector(100, 1000).detect(samples).size());
    }
}
