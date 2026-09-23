package xyz.theforks.calibration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import xyz.theforks.model.OSCMessageRecord;

/**
 * Pulls sensor readings for one address out of a recording.
 */
public class SampleExtractor {

    private SampleExtractor() {
    }

    /**
     * Extract samples from recorded messages.
     *
     * @param messages Recorded messages, in arrival order
     * @param addressPattern Regex the message address must match (same convention as node address patterns)
     * @param dimensions Number of leading numeric arguments that make up one reading
     * @return Samples with timestamps relative to the first matching message. Messages with fewer
     *         than {@code dimensions} arguments, or non-numeric ones, are skipped.
     */
    public static List<CalibrationSample> extract(List<OSCMessageRecord> messages, String addressPattern, int dimensions) {
        Pattern pattern = Pattern.compile(addressPattern);
        List<CalibrationSample> samples = new ArrayList<>();
        long firstTimestamp = -1;

        for (OSCMessageRecord record : messages) {
            if (record.getAddress() == null || !pattern.matcher(record.getAddress()).matches()) {
                continue;
            }
            double[] values = toValues(record.getArguments(), dimensions);
            if (values == null) {
                continue;
            }
            if (firstTimestamp < 0) {
                firstTimestamp = record.getTimestamp();
            }
            samples.add(new CalibrationSample(record.getTimestamp() - firstTimestamp, values));
        }
        return samples;
    }

    /**
     * Addresses in a recording whose messages start with numeric arguments, most frequent first.
     *
     * @return Address mapped to the number of leading numeric arguments in its first message
     */
    public static Map<String, Integer> numericAddresses(List<OSCMessageRecord> messages) {
        Map<String, Integer> dimensions = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (OSCMessageRecord record : messages) {
            String address = record.getAddress();
            if (address == null) {
                continue;
            }
            if (!dimensions.containsKey(address)) {
                int numeric = 0;
                Object[] args = record.getArguments();
                while (args != null && numeric < args.length && args[numeric] instanceof Number) {
                    numeric++;
                }
                if (numeric == 0) {
                    continue;
                }
                dimensions.put(address, numeric);
            }
            counts.merge(address, 1, Integer::sum);
        }
        List<String> addresses = new ArrayList<>(dimensions.keySet());
        addresses.sort(Comparator.comparing((String a) -> -counts.get(a)).thenComparing(a -> a));
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String address : addresses) {
            result.put(address, dimensions.get(address));
        }
        return result;
    }

    /**
     * Convert the first {@code dimensions} arguments to doubles.
     *
     * @return The values, or null if there are too few arguments or any is not numeric
     */
    public static double[] toValues(Object[] arguments, int dimensions) {
        if (arguments == null || arguments.length < dimensions) {
            return null;
        }
        double[] values = new double[dimensions];
        for (int i = 0; i < dimensions; i++) {
            if (!(arguments[i] instanceof Number)) {
                return null;
            }
            values[i] = ((Number) arguments[i]).doubleValue();
        }
        return values;
    }
}
