package xyz.theforks.nodes;

import com.illposed.osc.OSCMessage;

public interface OSCNode {
    String getAddressPattern();
    OSCMessage process(OSCMessage message);
    String getHelp();
    String label();
    int getNumArgs();
    boolean configure(String[] args);
    void showPreferences();
    String[] getArgs();
    String[] getArgNames();
}
