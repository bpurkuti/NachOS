package nachos.threads;

import nachos.machine.*;
import nachos.threads.PriorityScheduler.ThreadState;
import nachos.threads.PriorityScheduler.PriorityQueue;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

/**
 * A scheduler that chooses threads using a lottery.
 *
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 *
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 *
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking
 * the maximum).
 */
public class LotteryScheduler extends PriorityScheduler {
    /**
     * Allocate a new lottery scheduler.
     */
	
	public LotteryScheduler() {
		super();
	}
	
    /**
     * Allocate a new lottery thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer tickets from waiting threads
     *					to the owning thread.
     * @return	a new lottery thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new LotteryQueue(transferPriority);
    }
    
    public int getPriority(KThread thread) {
    	Lib.assertTrue(Machine.interrupt().disabled());
    	return getThreadState(thread).getPriority();
    }
    
    public int getEffectivePriority(KThread thread) {
    	Lib.assertTrue(Machine.interrupt().disabled());
    	return getThreadState(thread).getEffectivePriority();
    }
    
    public void setPriority(KThread thread, int priority) {
    	Lib.assertTrue(Machine.interrupt().disabled());
    	if (priority >= priorityMinimum && priority <= priorityMaximum) {
    		getThreadState(thread).setPriority(priority);
    	}
    }
    
    public boolean increasePriority() {
    	boolean interrupt = Machine.interrupt().disable();
    	KThread thread = KThread.currentThread();
    	
    	int priority = getPriority(thread);
    	if (priority == priorityMaximum) {
    		return false;
    	}
    	setPriority(thread, priority+1);
    	Machine.interrupt().restore(interrupt);
    	return true;
    }
    
    public boolean decreasePriority() {
    	boolean interrupt = Machine.interrupt().disable();
    	KThread thread = KThread.currentThread();
    	
    	int priority = getPriority(thread);
    	if (priority == priorityMinimum) {
    		return false;
    	}
    	setPriority(thread, priority-1);
    	Machine.interrupt().restore(interrupt);
    	return true;
    }
    
    public static final int priorityDefault = 1;
    public static final int priorityMinimum = 0;
    public static final int priorityMaximum = Integer.MAX_VALUE;
    
    protected ThreadState getThreadState(KThread thread) {
    	if (thread.schedulingState == null) {
    		thread.schedulingState = new ThreadState(thread);
    	}
    	return (ThreadState) thread.schedulingState;
    }
    
    protected class LotteryQueue extends PriorityQueue {
    	public LotteryQueue(boolean transferPriority){
    		super(transferPriority);
    	}
    	
    	public KThread nextThread() {
    		Lib.assertTrue(Machine.interrupt().disabled());
    		int totalTickets = 0;
    		Iterator<ThreadState> queue = LotteryPriority.iterator();
    		while (queue.hasNext()) {
    			int HeldTickets = queue.next().effectivePriority;
    			totalTickets += HeldTickets;
    		}
    		Random ticketGen = new Random();
    		int  ticketChoice = 0;
    		if (totalTickets != 0) {
    			ticketChoice = ticketGen.nextInt(totalTickets);
    		}
    		int ticketSubSum = 0;
    		KThread thread = null;
    		for (ThreadState TState : LotteryPriority) {
    			ticketSubSum += TState.effectivePriority;
    			if(ticketChoice < ticketSubSum) {
    				thread = TState.thread;
    				LotteryPriority.remove(TState);
    				break;
    			}
    		}
    		return thread;
    	}
    	
    	/*protected ThreadState pickNextThread() {
    		if (wait.isEmpty()) {
    			return null;
    		}
    		int ticketTotal = 0;
    		int [] sum = new int[wait.size()];
    		int i = 0;
    		for (KThread thread : wait) {
    			sum[i++] = ticketTotal += getThreadState(thread).getEffectivePriority();
    		}
    		int luckyNumber = random.nextInt(ticketTotal);
    		i = 0;
    		for (KThread thread : wait) {
    			if (luckyNumber < sum[i++]) {
    				return getThreadState(thread);
    			}
    		}
    		Lib.assertNotReached();
    		return null;
    	}
    	*/
    	public boolean transferPriority;
    	protected LotteryState LotterresourceHolder = null;
    	protected LinkedList<ThreadState> LotteryPriority = new LinkedList<ThreadState>();
    	
    protected class LotteryState extends ThreadState {
    	
    	protected KThread thread;
    	protected long age = Machine.timer().getTime();
    	protected int priority = priorityDefault;
    	protected int effectivePriority = priorityDefault;
    	protected LinkedList<LotteryQueue> ResourceHolders = new LinkedList<LotteryQueue>();
    	protected LinkedList<LotteryState> WaitingQueue = new LinkedList<LotteryState>();
    	
    	public LotteryState(KThread thread) {
    		super(thread);
    	}
    	
    	public int getPriority() {
    		return priority;
    	}
    	
    	public int getEffectivePriority() {
    		return this.effectivePriority;
    	}
    	
    	public void setPriority(int priority) {
    		if (this.priority == priority) {
    			return;
    		}
    		this.priority = priority;
    		this.effectivePriority = this.priority;
    		LotteryCleanup();
    	}
    	
    	private void LotteryCleanup() {
    		if(this.ResourceHolders != null) {
    			if(this.ResourceHolders.isEmpty()) {
    				Iterator<LotteryQueue> DonationQueue = this.ResourceHolders.descendingIterator();
    				while (DonationQueue.hasNext()) {
    					LotteryState HoldResource = DonationQueue.next().LotterresourceHolder;
    					int size = HoldResource.WaitingQueue.size();
    					int SigPriority = 0;
    					for (int i = 0; i < size; i++) {
    						SigPriority += HoldResource.WaitingQueue.get(i).effectivePriority;
    					}
    					
    					HoldResource.effectivePriority = priority + SigPriority;
    					HoldResource.LotteryCleanup();
    					
    				}
    			}
    		}
    	}
    	public void waitforAccess (LotteryQueue waitQueue) {
    		this.age = Machine.timer().getTime();
    		waitQueue.LotteryPriority.add(this);
    		if(waitQueue.transferPriority == true) {
    			ResourceHolders.add(waitQueue);
    			if(waitQueue.LotterresourceHolder != null) {
    				waitQueue.LotterresourceHolder.WaitingQueue.add(this);
    				waitQueue.LotterresourceHolder.effectivePriority += this.effectivePriority;
    				waitQueue.LotterresourceHolder.LotteryCleanup();
    			}
    		}
    		
    		
    	}
    	public void acquire(LotteryQueue waitQueue) {
    		if (waitQueue.transferPriority) {
    			waitQueue.LotterresourceHolder = this;
    		}
    	}
    }
    protected Random random = new Random(20);

    }
}