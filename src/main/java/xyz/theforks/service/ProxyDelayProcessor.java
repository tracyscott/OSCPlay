package xyz.theforks.service;

import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import com.illposed.osc.OSCMessage;

import xyz.theforks.model.MessageRequest;
import xyz.theforks.model.OSCMessageRecord;
import xyz.theforks.model.ScheduledMessage;

/**
 * Handles delayed message processing in proxy mode.
 * Similar to Playback's delay handling but for real-time proxying with DelayNode.
 */
public class ProxyDelayProcessor {
    private final PriorityQueue<ScheduledMessage> messageQueue;
    private final AtomicBoolean running;
    private Thread processorThread;
    private final OSCProxyService proxyService;

    public ProxyDelayProcessor(OSCProxyService proxyService) {
        this.proxyService = proxyService;
        this.messageQueue = new PriorityQueue<>();
        this.running = new AtomicBoolean(false);
    }

    /**
     * Start the delay processor thread.
     */
    public void start() {
        if (running.get()) {
            return;
        }

        running.set(true);
        processorThread = new Thread(() -> {
            System.out.println("ProxyDelayProcessor: Started");

            while (running.get()) {
                try {
                    ScheduledMessage scheduled = null;

                    synchronized (messageQueue) {
                        // Peek at the next message without removing it
                        if (!messageQueue.isEmpty()) {
                            ScheduledMessage next = messageQueue.peek();
                            long now = System.currentTimeMillis();

                            // If it's time to send this message, remove it from queue
                            if (next.getAbsoluteTimestamp() <= now) {
                                scheduled = messageQueue.poll();
                            }
                        }
                    }

                    if (scheduled != null) {
                        // Send the message
                        sendScheduledMessage(scheduled);
                    } else {
                        // No messages ready to send, sleep briefly
                        Thread.sleep(1);
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    System.err.println("ProxyDelayProcessor error: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            System.out.println("ProxyDelayProcessor: Stopped");
        });

        processorThread.setDaemon(true);
        processorThread.setName("ProxyDelayProcessor");
        processorThread.start();
    }

    /**
     * Stop the delay processor thread.
     */
    public void stop() {
        running.set(false);
        if (processorThread != null) {
            processorThread.interrupt();
            try {
                processorThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        synchronized (messageQueue) {
            messageQueue.clear();
        }
    }

    /**
     * Schedule a delayed message for sending.
     *
     * @param request The message request with delay
     * @param outputId The output ID to send through (null = all enabled outputs)
     */
    public void scheduleMessage(MessageRequest request, String outputId) {
        if (!running.get()) {
            System.err.println("ProxyDelayProcessor: Not running, cannot schedule message");
            return;
        }

        long absoluteTime = System.currentTimeMillis() + request.getDelayMs();

        // Create a new OSCMessageRecord from the request
        OSCMessageRecord record = new OSCMessageRecord(
            request.getMessage().getAddress(),
            request.getMessage().getArguments().toArray()
        );
        record.setTimestamp(absoluteTime);

        // Determine target output: use request's target if specified, otherwise the provided output
        String targetOutput = request.hasTargetOutput() ?
            request.getTargetOutputId() : outputId;

        ScheduledMessage scheduled = new ScheduledMessage(record, absoluteTime, targetOutput, request.getDelayMs());

        synchronized (messageQueue) {
            messageQueue.offer(scheduled);
        }

        System.out.println("ProxyDelayProcessor: Scheduled delayed message: " + record.getAddress() +
                         " delay=" + request.getDelayMs() + "ms output=" + targetOutput +
                         " queueSize=" + messageQueue.size());
    }

    /**
     * Send a scheduled message through the appropriate output(s).
     */
    private void sendScheduledMessage(ScheduledMessage scheduled) {
        try {
            if (scheduled.getRecord() == null ||
                scheduled.getRecord().getAddress() == null ||
                scheduled.getRecord().getArguments() == null) {
                return;
            }

            // Create OSC message
            OSCMessage oscMsg = new OSCMessage(
                scheduled.getRecord().getAddress(),
                List.of(scheduled.getRecord().getArguments())
            );

            // Route based on targetOutputId
            if (scheduled.getTargetOutputId() != null) {
                // Send to specific output only
                OSCOutputService targetOutput = proxyService.getOutput(scheduled.getTargetOutputId());
                if (targetOutput != null) {
                    sendToOutput(targetOutput, oscMsg, scheduled.getTargetOutputId(), scheduled.getPreviousDelay());
                }
            } else {
                // Send to all enabled outputs
                for (OSCOutputService output : proxyService.getOutputs()) {
                    if (output.isEnabled()) {
                        sendToOutput(output, oscMsg, output.getId(), scheduled.getPreviousDelay());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("ProxyDelayProcessor: Error sending scheduled message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Send message to a specific output, processing through its node chain.
     */
    private void sendToOutput(OSCOutputService output, OSCMessage message, String outputId, long previousDelay) {
        try {
            System.out.println("ProxyDelayProcessor: sendToOutput: " + message.getAddress() +
                             " previousDelay=" + previousDelay);

            // Process through output's node chain with previousDelay to prevent re-delaying
            List<MessageRequest> requests = output.getNodeChain().processMessage(message, null, previousDelay);

            System.out.println("ProxyDelayProcessor: After node chain, got " + requests.size() + " requests");
            for (MessageRequest req : requests) {
                System.out.println("  Request: delayMs=" + req.getDelayMs() +
                                 " previousDelay=" + req.getPreviousDelay() +
                                 " isImmediate=" + req.isImmediate());
                if (req.isImmediate()) {
                    // Send immediately
                    System.out.println("  -> Sending immediately to " + outputId);
                    output.send(req.getMessage(), true, true);  // bypass enabled check AND node chain
                } else {
                    // Schedule another delayed message (e.g., if DelayNode adds more delay)
                    System.out.println("  -> Scheduling delayed message");
                    scheduleMessage(req, outputId);
                }
            }
        } catch (Exception e) {
            System.err.println("ProxyDelayProcessor: Error sending to output: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Check if the processor is running.
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Get the current queue size (for debugging/monitoring).
     */
    public int getQueueSize() {
        synchronized (messageQueue) {
            return messageQueue.size();
        }
    }
}
