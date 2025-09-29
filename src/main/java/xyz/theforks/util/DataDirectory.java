package xyz.theforks.util;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages the application's user data directory.
 * All user-created data (recordings, audio, configs) is stored in:
 * ~/Documents/OSCPlay/
 *
 * This follows the standard convention across platforms.
 */
public class DataDirectory {

    private static final String APP_NAME = "OSCPlay";
    private static final String BASE_DIR_NAME = "Documents";

    // Subdirectories
    public static final String RECORDINGS_SUBDIR = "recordings";
    public static final String AUDIO_SUBDIR = "audio";
    public static final String CONFIG_SUBDIR = "config";

    private static Path baseDataDir;

    static {
        // Initialize base data directory
        String userHome = System.getProperty("user.home");
        baseDataDir = Paths.get(userHome, BASE_DIR_NAME, APP_NAME);

        // Create base directory if it doesn't exist
        File baseDirFile = baseDataDir.toFile();
        if (!baseDirFile.exists()) {
            baseDirFile.mkdirs();
        }
    }

    /**
     * Get the base data directory path.
     * @return Path to ~/Documents/OSCPlay/
     */
    public static Path getBaseDataDir() {
        return baseDataDir;
    }

    /**
     * Get the recordings directory path.
     * @return Path to ~/Documents/OSCPlay/recordings/
     */
    public static Path getRecordingsDir() {
        return baseDataDir.resolve(RECORDINGS_SUBDIR);
    }

    /**
     * Get the audio directory path.
     * @return Path to ~/Documents/OSCPlay/audio/
     */
    public static Path getAudioDir() {
        return baseDataDir.resolve(AUDIO_SUBDIR);
    }

    /**
     * Get the config directory path.
     * @return Path to ~/Documents/OSCPlay/config/
     */
    public static Path getConfigDir() {
        return baseDataDir.resolve(CONFIG_SUBDIR);
    }

    /**
     * Get a file path within the recordings directory.
     * @param filename The filename
     * @return Path to the file in recordings directory
     */
    public static Path getRecordingFile(String filename) {
        return getRecordingsDir().resolve(filename);
    }

    /**
     * Get a file path within the audio directory.
     * @param filename The filename
     * @return Path to the file in audio directory
     */
    public static Path getAudioFile(String filename) {
        return getAudioDir().resolve(filename);
    }

    /**
     * Get a file path within the config directory.
     * @param filename The filename
     * @return Path to the file in config directory
     */
    public static Path getConfigFile(String filename) {
        return getConfigDir().resolve(filename);
    }

    /**
     * Create all necessary subdirectories.
     */
    public static void createDirectories() {
        getRecordingsDir().toFile().mkdirs();
        getAudioDir().toFile().mkdirs();
        getConfigDir().toFile().mkdirs();
    }

    /**
     * Get the base data directory as a File object.
     * @return File representing base data directory
     */
    public static File getBaseDataDirFile() {
        return baseDataDir.toFile();
    }

    /**
     * Get the recordings directory as a File object.
     * @return File representing recordings directory
     */
    public static File getRecordingsDirFile() {
        return getRecordingsDir().toFile();
    }

    /**
     * Get the audio directory as a File object.
     * @return File representing audio directory
     */
    public static File getAudioDirFile() {
        return getAudioDir().toFile();
    }

    /**
     * Get the config directory as a File object.
     * @return File representing config directory
     */
    public static File getConfigDirFile() {
        return getConfigDir().toFile();
    }
}