package xyz.theforks.nodes;

public class NodeRegistry {
    private static final OSCNode[] nodes = {
        new PitchShiftNode(),
        new RenameNode(),
        new MovingAvgNode(),
        new IntToBangNode(),
        new PathTrimNode(),
        new DropNode(),
        new DelayNode(),
        new PassNode(),
        new ScriptNode(),
        new SplitterNode()
    };

    public static OSCNode[] getNodes() {
        return nodes;
    }

    public static String[] getNodeLabels() {
        String[] labels = new String[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            labels[i] = nodes[i].label();
        }
        return labels;
    }
}
