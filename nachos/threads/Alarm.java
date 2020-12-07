package nachos.threads;

import nachos.machine.*;
import nachos.security.Privilege;

import java.util.PriorityQueue;
import java.util.Queue;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {

    /**
     * A queue of threads waiting on this alarm
     */
    private PriorityQueue<WaitingThreadAdapter> waitingQueue = new PriorityQueue<WaitingThreadAdapter>();


    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
	Machine.timer().setInterruptHandler(new Runnable() {
		public void run() { timerInterrupt(); }
	    });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
        // check if it is time to run a thread or not
        while (waitingQueue.peek() != null && waitingQueue.peek().time < Machine.timer().getTime()) {
            // TODO: Not sure if atomic operation has to be enforced.
            boolean ints = Machine.interrupt().disable();
            // if ready, put back to ready
            KThread thread = waitingQueue.remove().thread;
            thread.ready();
            Machine.interrupt().restore(ints);
        }
        KThread.currentThread().yield();
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {

        long wakeTime = Machine.timer().getTime() + x;

        KThread kt = KThread.currentThread();
        waitingQueue.add(new WaitingThreadAdapter(kt, wakeTime));
        boolean intStatus = Machine.interrupt().disable();
        KThread.sleep();
        Machine.interrupt().restore(intStatus);
    }

    /**
     * A wrapper class for a {@link KThread} that holds extra information such as a time stamp
     * indicating when the thread should be waken up, and a comparator for the priority queue
     * to order all threads waiting on this alarm.
     */
    private static class WaitingThreadAdapter implements Comparable<WaitingThreadAdapter> {

        /**
         * The thread that is waiting
         */
        private KThread thread;

        /**
         * The time stamp of when the thread should be waken up
         */
        private long time;

        /**
         * Construct a thread adapter for the priority queue to use
         * @param thread the thread in wait
         * @param time until when should the thread wait
         */
        WaitingThreadAdapter(KThread thread, long time) {
            this.thread = thread;
            this.time = time;
        }

        @Override
        public int compareTo(WaitingThreadAdapter o) {
            return (int) (time - o.time);
        }
    }
}
