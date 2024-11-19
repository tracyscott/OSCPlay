package xyz.theforks.rewrite;

import com.illposed.osc.OSCMessage;

public interface RewriteHandler {
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
