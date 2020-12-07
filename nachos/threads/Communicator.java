package nachos.threads;

import java.util.ArrayList;
import java.util.List;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {

    private Lock lock;

    /**
     * Numbers of active speakers in {@link #activeSpeaker}. Should never exceed 1.
     */
    private int speakers;

    /**
     * Numbers of active listeners in {@link #activeListener}. Should never exceed 1.
     */
    private int listeners;

    /**
     * The word to communicate. Null if word is not ready.
     */
    private Integer message;

    /**
     * A condition queue/variable of the active speaker. No more than one speaker can wait on this condition. If more
     * speakers come in, wait on {@link #waitingSpeaker}.
     */
    private Condition2 activeSpeaker;

    /**
     * A condition queue/variable of the active listener. No more than one listener can wait on this condition. If more
     * listeners come in, wait on {@link #waitingListener}.
     */
    private Condition2 activeListener;

    /**
     * A condition queue/variable for incoming speakers to wait on. When {@link #speakers} counter go below 1, a
     * speaker will be waken up and put on wait in condition {@link #activeSpeaker}.
     */
    private Condition2 waitingSpeaker;

    /**
     * A condition queue/variable for incoming listeners to wait on. When {@link #listeners} counter go below 1, a
     * listener will be waken up and put on wait in condition {@link #activeListener}.
     */
    private Condition2 waitingListener;

    public static void selfTest() {
        final Communicator communicator = new Communicator();


        List<KThread> listeners = new ArrayList<KThread>();

        for (int i = 0; i < 10; i++) {
            listeners.add(new KThread(new Runnable() {

                @Override
                public void run() {
                    System.out.println("<<" + communicator.listen() + ">>");
                }
            }));
        }

        List<KThread> speakers = new ArrayList<KThread>();

        for (int i = 0; i < 10; i++) {
            final int ii = i;
            speakers.add(new KThread(new Runnable() {

                @Override
                public void run() {
                    communicator.speak(ii);
                }
            }));
        }

        KThread tl = new KThread(new Runnable() {
            @Override
            public void run() {
                System.out.printf("!!!!!!!Listen: %s", communicator.listen());
            }
        });

        KThread ts = new KThread(new Runnable() {
            @Override
            public void run() {
                communicator.speak(2345);
            }
        });
//        tl.fork();
//        ts.fork();

        for (int i = 0; i < speakers.size(); i++) {
            speakers.get(i).fork();
        }

        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).fork();
        }
    }

    public Communicator() {
        lock = new Lock();
        listeners = 0;
        speakers = 0;
        message = null;
        this.activeSpeaker = new Condition2(lock);
        this.activeListener = new Condition2(lock);
        this.waitingSpeaker = new Condition2(lock);
        this.waitingListener = new Condition2(lock);
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param    word    the integer to transfer.
     */
    public void speak(int word) {
        lock.acquire();  // acquires lock for critical section

        // if there is a active speaker, wait until it finishes.
        while (speakers > 0) {
            waitingSpeaker.sleep();
        }

        // increment the numbers of active speaker
        speakers++;

        // when there are no listeners or the message isn't empty, speak will go to sleep
        while (message != null || listeners == 0) {
            activeSpeaker.sleep();
        }

        // wakes up
        message = word; // we transfer the word to message; the message is the word that speak will keep
        activeListener.wake(); // we wake up listen

        // done. decrement the active speaker count and let the next speaker come in.
        speakers--;
        waitingSpeaker.wake();

        lock.release(); // release the lock and exit

    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return the integer transferred.
     */
    public int listen() {
        lock.acquire(); // acquires lock for "critical" section

        // if there is a active listener, wait until it finishes
        while (listeners > 0) {
            waitingListener.sleep();
        }

        // a listener is being called so add to the count
        listeners++;

        while (message == null) {      // while there is no message
            activeSpeaker.wake();      // wake up speaker and
            activeListener.sleep();    // put listen to sleep
        }
        int word = message;  // set the word to be transferred
        message = null;      // mark the message as "not ready"


        // done. decrement the active listener count and let the next listener come in.
        listeners--;
        waitingListener.wake();

        lock.release();    // release the lock and exit

        return word; // the word should be the result of return
    }
}
