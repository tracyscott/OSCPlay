package xyz.theforks;

import java.net.InetSocketAddress;
import java.util.Arrays;

import com.illposed.osc.OSCMessage;
import com.illposed.osc.transport.OSCPortOut;
import com.illposed.osc.transport.OSCPortOutBuilder;

public class TestOSCSender {
    public static void main(String[] args) {
        try {
            // Create sender
            OSCPortOut sender = new OSCPortOutBuilder()
            .setRemoteSocketAddress(new InetSocketAddress("127.0.0.1", 3030))
            .build();

            // Create test message
            OSCMessage message = new OSCMessage("/test", Arrays.asList(1, 2, 3));

            // Send message repeatedly
            while (true) {
                sender.send(message);
                System.out.println("Sent test message");
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}