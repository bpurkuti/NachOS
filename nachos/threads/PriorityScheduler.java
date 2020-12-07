package nachos.threads;

import nachos.machine.*;

import java.util.HashSet;
//needed for LinkedList
import java.util.LinkedList;
//needed imports for list implementation 
import java.util.List;


/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
    }
    
    /**
     * Allocate a new priority thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer priority from waiting threads
     *					to the owning thread.
     * @return	a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
    	return new PriorityQueue(transferPriority);
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
		       
    	Lib.assertTrue(priority >= priorityMinimum &&
    			priority <= priorityMaximum);
	
    	getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
    	boolean intStatus = Machine.interrupt().disable();
		       
    	KThread thread = KThread.currentThread();

    	int priority = getPriority(thread);
    	if (priority == priorityMaximum)
    		return false;

    	setPriority(thread, priority+1);
    	
    	Machine.interrupt().restore(intStatus);
    	return true;
    }

    public boolean decreasePriority() {
    	boolean intStatus = Machine.interrupt().disable();
		       
    	KThread thread = KThread.currentThread();

    	int priority = getPriority(thread);
    	if (priority == priorityMinimum) {
    		return false;
    	}

    	setPriority(thread, priority-1);

    	Machine.interrupt().restore(intStatus);
    	return true;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;    

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param	thread	the thread whose scheduling state to return.
     * @return	the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
	if (thread.schedulingState == null)
	    thread.schedulingState = new ThreadState(thread);

	return (ThreadState) thread.schedulingState;
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue {
    	PriorityQueue(boolean transferPriority) {
    		this.transferPriority = transferPriority;
    		//set to new LinkedList
    		this.wait = new LinkedList<ThreadState>();
	}

	public void waitForAccess(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    //new fState
	    //set to threads state
	    final ThreadState fState = getThreadState(thread);
	    //add intState to wait
	    this.wait.add(fState);
	    //wait
	    fState.waitForAccess(this);
	    //deleted
	    //getThreadState(thread).waitForAccess(this);
	}

	public void acquire(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    //final thread state
        final ThreadState fState = getThreadState(thread);
        //checks if not null
        if (this.hold != null) {
        	//release
            this.hold.release(this);
        }
        //set to fState
        this.hold = fState;
        //get
        fState.acquire(this);
	    
	    //deleted
	    //getThreadState(thread).acquire(this);
	}

	public KThread nextThread() {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    // implement me
        //get next thread
	    final ThreadState NextT = this.pickNextThread();
	    //check if empty
        if (NextT == null) {
        	return null;
        }
        //remove next thread
        this.wait.remove(NextT);
        //transfer to next thread
        this.acquire(NextT.getThread());
        //return thread
        return NextT.getThread();

	    //deleted
	    //return null;
	}

	//new ThreadState
	//returns next threads
	//doesn't modify queue
	protected ThreadState pickNextThread() {
        //nextPriority set to minimum priority
		int nextPriority = priorityMinimum;
        //next set to empty (null)
		ThreadState next = null;
		//loop through threads
        for (final ThreadState currThread : this.wait) {
        	//set currentPriority to effective priority
            int currPriority = currThread.getEffectivePriority();
            //check in next threads exist, or if priority greater than next
            if (next == null || (currPriority > nextPriority)) {
            	//set next to current thread
                next = currThread;
                //change nextPriorities thread to current threads
                nextPriority = currPriority;
            }
        }
     //return X
     return next;
     }

    public void print() {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    // implement me (if you want)
	    //loop for threads
       for (final ThreadState state : this.wait) {
       	//print priority
           System.out.println(state.getEffectivePriority());
       }
	}
	
	/**
	 * Return the next thread that <tt>nextThread()</tt> would return,
	 * without modifying the state of this queue.
	 *
	 * @return	the next thread that <tt>nextThread()</tt> would
	 *		return.
	 */
    //int getEffectivePriority
     public int getEffectivePriority() {
          //if not set to transfer priority
    	 if (!this.transferPriority) {
            //return min priority  
    		return priorityMinimum;
            }  
    	 //check if priority change
    	 else if (this.priorityChange) {
                //set effective priority to min
                this.effectivePriority = priorityMinimum;
                //loop 
                for (final ThreadState curr : this.wait) {
                    //get new effective priority
                	this.effectivePriority = Math.max(this.effectivePriority, curr.getEffectivePriority());
                }
                //false
                this.priorityChange = false;
            }	
    	 	//return effective priority
            return effectivePriority;		
        }
	
	//new invalidateCachedPriority
    private void invalidateCachedPriority() {
        //check if not transferPriority
    	if (!this.transferPriority) { 
        	return;
    	}
    	//set priority change to true
        this.priorityChange = true;
        //check if not null
        if (this.hold != null) {
            //invalid
        	hold.invalidateCachedPriority();
        }
    }
    //new list for waiting threads
    protected final List<ThreadState> wait;
    //current resources being held
    protected  ThreadState hold = null;
    //change effectivePriority
    //set to min
    protected int effectivePriority = priorityMinimum;
    //effective priority is invalid
    protected boolean priorityChange = false;
    
    public boolean transferPriority;
}
	/**
	 * <tt>true</tt> if this queue should transfer priority from waiting
	 * threads to the owning thread.
	 */

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see	nachos.threads.KThread#schedulingState
     */
    protected class ThreadState {
	/**
	 * Allocate a new <tt>ThreadState</tt> object and associate it with the
	 * specified thread.
	 *
	 * @param	thread	the thread this state belongs to.
	 */
    //new HashSet called acquireHashSet of type	ThreadQueue
    protected HashSet<ThreadQueue> acquireHashSet = new HashSet<ThreadQueue>();
    //new HashSet called waitingHashSet of type ThreadQueue
    protected HashSet<ThreadQueue> waitingHashSet = new HashSet<ThreadQueue>();
		
	public ThreadState(KThread thread) {
	    this.thread = thread;
	    //new
        this.currentResourceHolder = new LinkedList<PriorityQueue>();
        //new
        this.waitingResources = new LinkedList<PriorityQueue>();
        

	    
	    setPriority(priorityDefault);
	}

	/**
	 * Return the priority of the associated thread.
	 *
	 * @return	the priority of the associated thread.
	 */
	public int getPriority() {
	    return priority;
	}

	/**
	 * Return the effective priority of the associated thread.
	 *
	 * @return	the effective priority of the associated thread.
	 */
	public int getEffectivePriority() {
	    // implement me
	   
		//new int effectivePriority
		//set to getEffectivePriority()
		int effectivePriority = getEffectivePriority();
		//check if not equal
			if (effectivePriority != effectivePriority) {
		}
		//if empty resources
        if (this.currentResourceHolder.isEmpty()) {
            //return priority
        	return this.getPriority();
        } 
        //if priority change
        else if (this.priorityChange) {
            //set effective priority
        	this.effectivePriority = this.getPriority();
        	//loop
            for (final PriorityQueue pq : this.currentResourceHolder) {
                //calculate new priority
            	this.effectivePriority = Math.max(this.effectivePriority, pq.getEffectivePriority());
            }
            //false
            this.priorityChange = false;
        }
        //return effective priority
        return this.effectivePriority;
	}

	/**
	 * Set the priority of the associated thread to the specified value.
	 *
	 * @param	priority	the new priority.
	 */
	public void setPriority(int priority) {
	    if (this.priority == priority) {
	    	return;
	    }
	    //sets setPriority argument (priority) to priority
	    this.priority = priority;
	    //loop
        for (final PriorityQueue priQueue : waitingResources) {
    		//set
        	priQueue.invalidateCachedPriority();
        }
	}

	/**
	 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
	 * the associated thread) is invoked on the specified priority queue.
	 * The associated thread is therefore waiting for access to the
	 * resource guarded by <tt>waitQueue</tt>. This method is only called
	 * if the associated thread cannot immediately obtain access.
	 *
	 * @param	waitQueue	the queue that the associated thread is
	 *				now waiting on.
	 *
	 * @see	nachos.threads.ThreadQueue#waitForAccess
	 */
	public void waitForAccess(PriorityQueue waitQueue) {
	    // implement me
		
		//add to waitingHashSet
		waitingHashSet.add(waitQueue);
		//add
        this.waitingResources.add(waitQueue);
        //remove 
        this.currentResourceHolder.remove(waitQueue);
        //clear
        waitQueue.invalidateCachedPriority();
	}
	/**
	 * Called when the associated thread has acquired access to whatever is
	 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
	 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
	 * <tt>thread</tt> is the associated thread), or as a result of
	 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
	 *
	 * @see	nachos.threads.ThreadQueue#acquire
	 * @see	nachos.threads.ThreadQueue#nextThread
	 */
	public void acquire(PriorityQueue waitQueue) {
	    // implement me
		//remove from waitingHashSet
		waitingHashSet.remove(waitQueue);
		//add to acquireHashSet
		acquireHashSet.add(waitQueue);
        //add
		this.currentResourceHolder.add(waitQueue);
        //remove
        this.waitingResources.remove(waitQueue);
        //clear
        this.invalidateCachedPriority();
			
		//no interrupts
		//deleted
	 	//Lib.assertTrue(Machine.interrupt().disabled()); 
    }

	//new release
    public void release(PriorityQueue waitQueue) {
    	//remove form acquireHashSet
    	acquireHashSet.remove(waitQueue);  	
    	//remove form queue
        this.currentResourceHolder.remove(waitQueue);
        //call
        this.invalidateCachedPriority();
    }

    public KThread getThread() {
        return thread;
    }

    private void invalidateCachedPriority() {
        if (this.priorityChange) return;
        this.priorityChange = true;
        for (final PriorityQueue priQue : this.waitingResources) {
        		priQue.invalidateCachedPriority();
        }
    }
    //new boolean call priorityChange
    //set to false, true if effective priority invalid
    protected boolean priorityChange = false; 
    //effective priority of this Thread State.
    protected int effectivePriority = priorityMinimum; 
    
    /** The thread with which this object is associated. */	   
	protected KThread thread;
	/** The priority of the associated thread. */
	protected int priority;
	
	//list queue for all waiting resources 
    protected final List<PriorityQueue> waitingResources;
	//list queue for current resource holder.
	protected final List<PriorityQueue> currentResourceHolder; 

    
    }
}
