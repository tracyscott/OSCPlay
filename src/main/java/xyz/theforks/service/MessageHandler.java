package xyz.theforks.service;

import java.io.IOException;

import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCSerializeException;


abstract class MessageHandlerClass {
    abstract public void handleMessage(OSCMessage message) throws IOException, OSCSerializeException;
}

@FunctionalInterface
public interface MessageHandler {
    void handleMessage(OSCMessage message);
}
