package nachos.threads;
import nachos.ag.BoatGrader;

public class Boat {
	static BoatGrader bg;
	public static void selfTest() {
		BoatGrader b = new BoatGrader();
//
//		System.out.println("\n ***Testing Boats with only 2 children***");
//		begin(0, 2, b);
//		System.out.println("ChildOhau: " + childOhauCount+ " ,ChildMolokai: " + childMolokaiCount);
//
//		System.out.println("\n ***Testing Boats with only 1 adult***");
//		begin(1, 0, b);
//		System.out.println("AdultOhau: " + adultOhauCount+ " ,AdultMolokai: " + adultMolokaiCount);


		begin(1, 2, b);
		System.out.println("ChildOhau: " + childOhauCount+ " ,ChildMolokai: " + childMolokaiCount);
		System.out.println("AdultOhau: " + adultOhauCount+ " ,AdultMolokai: " + adultMolokaiCount);

	}

	public static void begin(int adults, int children, BoatGrader b) {
		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		bg = b;

		// Instantiate global variables
		childOhauCount = children; // # of children at oahu
		adultOhauCount = adults; // # of adults at oahu
		childMolokaiCount = 0; // # of children at Molokai
		adultMolokaiCount = 0; // # of adults at Molokai

		passengers = 0; // # of passengers on the boat child counts for 1, adult for 2

		boatIsAto = true; // Boat location, Boat is At Ohau... True denotes boat at Ohau, false denotes that it is at Molokai

		// Create threads here. See section 3.4 of the Nachos for Java
		// Walkthrough linked from the projects page.

		// child runnable
		// child threads
		for (int i = 0; i < children; i++) {
			Runnable c = new Runnable() {
				public void run() {
					ChildItinerary();
				}
			};
			//new child thread
			KThread childThread = new KThread(c);
			//fork child
			childThread.fork();
		}

		// adult runnable
		// adult threads
		for (int i = 0; i < adults; i++) {
			Runnable a = new Runnable() {
				public void run() {
					AdultItinerary();
				}
			};
			//new adult thread
			KThread adultThread = new KThread(a);
			//fork adult
			adultThread.fork();
		}
		threadStatus.P();
	}

	/*
	The idea is to send one adult over at a time(since boat can only fit one adult), ONLY if there is at least one child
		in Molokai to bring the boat back to Oahu(assuming there is more to bring to Molokai).
	An adult thread never comes back from Molokai.
	 */
	static void AdultItinerary() {
		//get lock to allow the following to happen to the thread before leaving the lock to another thread
		lock.acquire();

		/*
		adults are kept asleep unless there are no more children on Oahu
		 	and there are children on Molokai(to bring the boat back) or the boat isn't on Oahu
		 */
		while((childOhauCount > 1 && childMolokaiCount == 0) || !boatIsAto){
			adultOnOhau.sleep();	//thread is slept, cannot continue following orders of the program unless woken
		}

		//the adults count for 2 passengers, as one gets on the boat the passenger counter increases and
		//the number of adults on Oahu decreases
		passengers+=2;
		adultOhauCount--;

		//adult is sent to Molokai
		bg.AdultRowToMolokai();

		//after arriving the adult gets off the boat, decreasing passenger and increasing the # of adults on Molokai
		passengers-=2;
		adultMolokaiCount++;

		//boat location is updated to Moloka(false)
		boatIsAto=false;
		//since the boat can only return with a child a child thread is awoken on molokai
		childOnMolokai.wake();

		//lock is realeased
		lock.release();

		threadStatus.V();
	}

	static void ChildItinerary() {
		//get lock to allow the following to happen to the thread before leaving the lock to another thread
		lock.acquire();

		/*
		children are only allowed to move if there is any one on Oahu, because the only way to move the
			people to Molokai is to juggle the boat between children going to Molokai and coming back.
		 */
		while((childOhauCount + adultOhauCount)!= 0){
			//sleep thread if the boat is unavailable(full or in Molokai) or if there are
			//|| ( adultOhauCount>0 && childMolokaiCount>0) with this condition it seems to stop sending children after 2
			while(!boatIsAto || passengers>=2 ){
				childOnOhau.sleep();
			}

			/*
			this is the general case: boat is empty and in Oahu
			The first child(if condition) wakes the second on Oahu and then gets on the boat and
				sleeps waiting to be awoken by the second. Then it rides to Molokai and wakes the first after it
				falls asleep in the boat having arrived.
			The second child(else if) enters the boat and wakes the first. Then it rows to Molokai and sleeps
				to be awoken again by the first after it(the first) has done riding to Molokai.
			 */
			if(passengers==0)
			{
				//first child wakes second on Oahu
				childOnOhau.wake();
				//first child gets on the boat incrementing passengers and decreasing # children on Oahu
				passengers++;
				childOhauCount--;

				//it sleeps to be awoken by second
				onBoat.sleep();
				//Rides to molokai after being awoken
				bg.ChildRideToMolokai();

				//first child gets off the boat decreasing passengers and increasing # children on Molokai
				passengers--;
				childMolokaiCount++;
				//first child wakes the second after it sleeps after it's done rowing
				onBoat.wake();
			}
			else if(passengers==1)
			{
				//second child gets on the boat incrementing passengers and decreasing # children on Oahu
				passengers++;
				childOhauCount--;
				//it wakes the first
				onBoat.wake();

				//Rows to Molokai
				bg.ChildRowToMolokai();
				//second child gets off the boat decreasing passengers and increasing # children on Molokai
				passengers--;
				childMolokaiCount++;

				//sleeps to be awoken by first after the second rides to molokai
				onBoat.sleep();
			}
			//boat is in Molokai = false -> because of the while loop the if/else if statements must have happened
			boatIsAto=false;
			//we consider whether to wake an adult thread based off number of adults and children in Oahu
			if(adultOhauCount > 0 && childOhauCount == 1){
				adultOnOhau.wake();
			}
			//while loop to check if we can send a child back to Oahu to use the boat from there
			while(boatIsAto || passengers > 0) {
				childOnMolokai.sleep();
			}
			//if passed the while loop the number of passengers is checked
			//only once child allowed on the boat to go back
			if (passengers == 0) {
				//passenger is incremented and # children on Molokai decreased
				passengers++;
				childMolokaiCount--;

				//child rows back to Oahu
				bg.ChildRowToOahu();

				//boat location is updated
				boatIsAto=true;
				//passengers is decremented and # of children on Oahu is increased
				passengers--;
				childOhauCount++;
			}

			//given the edge case of only 1 child on Oahu and no adults
			if(childOhauCount == 1 && adultOhauCount == 0){
				//child is woken
				childOnOhau.wake();
				//it rows to molokai alone since its the only one left
				bg.ChildRowToMolokai();
				//island population is updated
				childOhauCount--;
				childMolokaiCount++;
				//boat location is updated
				boatIsAto = false;
			}
			/*
			Our group is aware that the logic of the Boat.java file is flawed as it cannot satisfy most test cases
				outside of the basic ones.
			We assume the error to be within ChildItinerary and to be some form of condition that doesn't allow
				for more than one adult and for not all the children to be sent. We may be waking some threads
				without allowing them to do anything or we may have logic that concludes the program prematurely.
			Ideally we would have some code here to wake all the threads in Molokai after having moved them there
				but we thought adding that at this stage might cause ulterior complications.
			 */
		}
		//release lock
		lock.release();
		threadStatus.V();
	}

	static Lock lock = new Lock();	//Lock for boat
	// Condition Variables-->Have to initialize it with a lock
	static Condition2 onBoat = new Condition2(lock);
	static Condition2 childOnMolokai = new Condition2(lock);
	static Condition2 childOnOhau = new Condition2(lock);
	static Condition2 adultOnMolokai = new Condition2(lock);
	static Condition2 adultOnOhau = new Condition2(lock);

	//Global Variables
	//explained inside begin
	private static int childOhauCount;
	private static int adultOhauCount;
	private static int childMolokaiCount;
	private static int adultMolokaiCount;
	private static int passengers;
	private static boolean boatIsAto;

	static Semaphore threadStatus = new Semaphore(0);
}
