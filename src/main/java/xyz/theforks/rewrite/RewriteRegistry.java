package xyz.theforks.rewrite;

public class RewriteRegistry {
    private static final RewriteHandler[] handlers = {
        new PitchShiftHandler(),
        new InterlaceMagHandler(),
        new RenameHandler(),
        new MovingAvgHandler(),
        new IntToBangHandler(),
        new PathTrimHandler()
    };

    public static RewriteHandler[] getHandlers() {
        return handlers;
    }

    public static String[] getHandlerLabels() {
        String[] labels = new String[handlers.length];
        for (int i = 0; i < handlers.length; i++) {
            labels[i] = handlers[i].label();
        }
        return labels;
    }
}