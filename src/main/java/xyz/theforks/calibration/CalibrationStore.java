package xyz.theforks.calibration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Saves and loads calibrations as {name}.json in a directory, normally the current
 * project's Calibrations/ directory.
 */
public class CalibrationStore {
    private static final String EXTENSION = ".json";

    private final Path dir;
    private final ObjectMapper mapper = new ObjectMapper();

    public CalibrationStore(Path dir) {
        this.dir = dir;
    }

    public Path getDir() {
        return dir;
    }

    public Path pathFor(String name) {
        return dir.resolve(name + EXTENSION);
    }

    public boolean exists(String name) {
        return Files.exists(pathFor(name));
    }

    public void save(Calibration calibration) throws IOException {
        Files.createDirectories(dir);
        mapper.writerWithDefaultPrettyPrinter().writeValue(pathFor(calibration.getName()).toFile(), calibration);
    }

    public Calibration load(String name) throws IOException {
        Path path = pathFor(name);
        if (!Files.exists(path)) {
            throw new IOException("Calibration not found: " + path);
        }
        Calibration calibration = mapper.readValue(path.toFile(), Calibration.class);
        calibration.setName(name);
        return calibration;
    }

    /**
     * Names of the saved calibrations, sorted.
     */
    public List<String> list() throws IOException {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return names;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.map(p -> p.getFileName().toString())
                .filter(f -> f.endsWith(EXTENSION))
                .map(f -> f.substring(0, f.length() - EXTENSION.length()))
                .sorted()
                .forEach(names::add);
        }
        return names;
    }
}
