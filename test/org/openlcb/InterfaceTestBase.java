package org.openlcb;

import org.junit.After;
import org.junit.Before;

import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.mockito.verification.VerificationMode;
import org.openlcb.can.AliasMap;
import org.openlcb.can.CanFrame;
import org.openlcb.can.GridConnect;
import org.openlcb.can.MessageBuilder;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

import static org.mockito.Mockito.*;

/**
 * Test helper class that instantiates an OlcbInterface and allows making expectations on what is
 * sent to the bus, as well as allows injecting response messages from the bus.
 *
 * Created by bracz on 1/9/16.
 */
public abstract class InterfaceTestBase {
    protected Connection outputConnectionMock = spy(new PrintConnection());
    protected OlcbInterface iface = null;
    protected AliasMap aliasMap = new AliasMap();
    protected boolean testWithCanFrameRendering = false;
    private boolean debugFrames = false;
    private FakeConnection fakeConnection;

    @Before
    public void setUp() {
        expectInit();
    }

    private void expectInit() {
        NodeID id = new NodeID(new byte[]{1,2,0,0,1,1});
        aliasMap.insert(0x333, id);
        fakeConnection = new FakeConnection(outputConnectionMock);
        iface = new OlcbInterface(id, fakeConnection);
        expectMessage(new InitializationCompleteMessage(iface.getNodeId()));
        //enableSingleThreaded();
    }

    @After
    public void tearDown() {
        expectNoMessages();
        iface.dispose();
        iface = null;
    }

    private class PrintConnection extends AbstractConnection {
        @Override
        public void put(Message msg, Connection sender) {
            System.out.println("S: " + msg.toString());
        }
    }

    public void enableSingleThreaded() {
        FakeExecutionThread thread = new FakeExecutionThread();
        iface.setLoopbackThread(thread);
        iface.runOnThreadPool(thread);
    }

    class FakeExecutionThread implements OlcbInterface.SyncExecutor, Runnable {
        private final BlockingQueue<QEntry> outputQueue = new
                LinkedBlockingQueue<>();

        @Override
        public void schedule(Runnable r) throws InterruptedException {
            QEntry q = new QEntry(r);
            outputQueue.add(q);
            q.sem.acquire();
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                QEntry m = null;
                try {
                    m = outputQueue.take();
                    m.callback.run();
                    m.sem.release();
                } catch (InterruptedException e) {
                    return;
                }
            }
        }

        private class QEntry {
            QEntry(Runnable r) {
                callback = r;
            }
            Runnable callback;
            Semaphore sem = new Semaphore(0);
        }
    }

    /// Prints all messages that get sent to the mock. For debugging purposes.
    public void printAllSentMessages() {
        fakeConnection.debugMessages = true;
    }

    /** Sends one or more OpenLCB message, as represented by the given CAN frames, to the
     * interface's inbound port. This represents traffic that a far away node is sending. The
     * frame should be specified in the GridConnect protocol.
     * @param frames is one or more CAN frames in the GridConnect protocol format.
     *  */
    protected void sendFrame(String frames) {
        List<CanFrame> parsedFrames = GridConnect.parse(frames);
        MessageBuilder d = new MessageBuilder(aliasMap);
        for (CanFrame f : parsedFrames) {
            List<Message> l = d.processFrame(f);
            if (l != null) {
                for (Message m : l) {
                    iface.getInputConnection().put(m, null);
                }
            }
        }
    }

    /** Sends an OpenLCB message to the interface's inbound port. This represents traffic that a
     * far away node is sending.
     * @param msg inbound message from a far node
     */
    protected void sendMessage(Message msg) {
        if (testWithCanFrameRendering) {
            MessageBuilder d = new MessageBuilder(aliasMap);
            List<? extends CanFrame> actualFrames = d.processMessage(msg);
            StringBuilder b = new StringBuilder();
            for (CanFrame f : actualFrames) {
                b.append(GridConnect.format(f));
            }
            if (debugFrames)System.err.println("Input frames: " + b);
            sendFrame(b.toString());
        } else {
            iface.getInputConnection().put(msg, null);
        }
    }

    /** Moves all outgoing messages to the pending messages queue. */
    protected void consumeMessages() {
        iface.flushSendQueue();
    }

    /** Expects that the next outgoing message (not yet matched with an expectation) is the given
     * CAN frame.
     * @param expectedFrame GridConnect-formatted CAN frame.
     * @param cardinality   how many times to expect this frame, e.g. 'times(2)' or omit for once.
     */
    protected void expectFrame(String expectedFrame, VerificationMode cardinality) {
        class MessageMatchesFrame implements ArgumentMatcher<Message> {
            private final String frame;

            public MessageMatchesFrame(String frame) {
                this.frame = frame;
            }
            public boolean matches(Message message) {
                MessageBuilder d = new MessageBuilder(aliasMap);
                List<? extends CanFrame> actualFrames = d.processMessage(message);
                StringBuilder b = new StringBuilder();
                for (CanFrame f : actualFrames) {
                    b.append(GridConnect.format(f));
                }
                return frame.equals(b.toString());
            }
            public String toString() {
                //printed in verification errors
                return "[OpenLCB message with CAN rendering of " + frame + "]";
            }
        }

        consumeMessages();
        verify(outputConnectionMock, cardinality).put(
                argThat(new MessageMatchesFrame(expectedFrame)),any());
    }

    protected void expectFrame(String expectedFrame) {
        expectFrame(expectedFrame, times(1));
    }


    /** Expects that the next outgoing message (not yet matched with an expectation) is the given
     * message.
     * @param expectedMessage message that should have been sent to the bus from the local stack.
     */
    protected void expectMessage(Message expectedMessage) {
        consumeMessages();
        verify(outputConnectionMock).put(eq(expectedMessage), any());
    }

    protected void expectMessageAndNoMore(Message expectedMessage) {
        expectMessage(expectedMessage);
        expectNoMessages();
    }

    /** Expects that there are no unconsumed outgoing messages. */
    protected void expectNoFrames() {
        consumeMessages();
        verifyNoMoreInteractions(outputConnectionMock);
        clearInvocations(outputConnectionMock);
    }

    /** Expects that there are no unconsumed outgoing messages. */
    protected void expectNoMessages() {
        expectNoFrames();
    }

    protected void sendFrameAndExpectResult(String send, String expect) {
        sendFrame(send);
        expectFrame(expect);
        expectNoFrames();
    }

    protected void sendMessageAndExpectResult(Message send, Message expect) {
        sendMessage(send);
        expectMessage(expect);
    }
}
