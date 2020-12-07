package nachos.userprog;

import nachos.machine.*;
import nachos.threads.Lock;
import nachos.threads.ThreadedKernel;

import java.io.EOFException;
import java.util.*;
import java.util.Map.Entry;
import java.util.Iterator;

import static java.lang.Math.min;


//use a global datastructure like a linked list to keep track of the maximum number of pages available.
// The resource should be easily freeable and accessible by all processes(ideally not at once but for now we
// will .


/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see    nachos.vm.VMProcess
 * @see    nachos.network.NetProcess
 */
public class UserProcess {

    Map<Integer, UserProcess> childProcessList;

    /**
     * Allocate a new process.
     */
    public UserProcess() {
        //creating unique processIDs for each process
        pid=uniquepid++;
        Pstatus=1;

        //Each process has its own childProcessList
        childProcessList= new HashMap<Integer, UserProcess>();

        fileDescriptors.put(0, UserKernel.console.openForReading());
        fileDescriptors.put(1, UserKernel.console.openForWriting());
        int numPhysPages = Machine.processor().getNumPhysPages();
        pageTable = new TranslationEntry[numPhysPages];
        for (int i = 0; i < numPhysPages; i++)
            pageTable[i] = new TranslationEntry(i, i, true, false, false, false);

        //inserting into the hashmap
        //childProcessList.put(pid, new UserProcess());

    }

    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
        return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param    name    the name of the file containing the executable.
     * @param    args    the arguments to pass to the executable.
     * @return    <tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
        if (!load(name, args))
            return false;

        thread = new UThread(this);
        thread.setName(name).fork();

        return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
        Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param    vaddr    the starting virtual address of the null-terminated
     * string.
     * @param    maxLength    the maximum number of characters in the string,
     * not including the null terminator.
     * @return the string read, or <tt>null</tt> if no null terminator was
     * found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
        Lib.assertTrue(maxLength >= 0);

        byte[] bytes = new byte[maxLength + 1];

        int bytesRead = readVirtualMemory(vaddr, bytes);

        for (int length = 0; length < bytesRead; length++) {
            if (bytes[length] == 0)
                return new String(bytes, 0, length);
        }

        return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param    vaddr    the first byte of virtual memory to read.
     * @param    data    the array where the data will be stored.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
        return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param    vaddr    the first byte of virtual memory to read.
     * @param    data    the array where the data will be stored.
     * @param    offset    the first byte to write in the array.
     * @param    length    the number of bytes to transfer from virtual memory to
     * the array.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
                                 int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

	byte[] memory = Machine.processor().getMemory();
	
	int copied = 0;
	int bitPerPage = 1024;//1024 bits = 1 page

	while(data.length > offset && length > 0){
		int addressOffset = vaddr % bitPerPage;
		int virtPageNum = vaddr / bitPerPage;

		if(virtPageNum > pageTable.length || virtPageNum < 0)
			break; //pages are released

		TranslationEntry pageTentry = pageTable[virtPageNum];
			if(!pageTentry.valid)
				break;

		pageTentry.used = true;
		int physPageNum = pageTentry.ppn;
		int physAddress = physPageNum * 1024 + addressOffset;

		pageTentry.dirty = true;
		int copiedLength = Math.min(data.length - offset, Math.min(length, 1024 - addressOffset));

		System.arraycopy(memory, physAddress, data, offset, copiedLength);
		vaddr = vaddr + copiedLength;
		offset = offset + copiedLength;
		length = length - copiedLength;
		copied = copied + copiedLength;
	}

	return copied;

	/*int vPage = Processor.pageFromAddress(vaddr);
	int pgOffset = Processor.offsetFromAddress(vaddr);
	int bytesLeft = length;
	int buffOffset = offset;
	int copied = 0;
	if (vaddr < 0)
		return 0;

	while(bytesLeft > 0){

		if(Processor.pageFromAddress(vaddr + copied) >= numPages){
			break;
		}

		int bytesToEndPg = (pageSize - pgOffset);
		int bytesToCopy = min(bytesToEndPg, bytesLeft);
		int pAddr = Processor.makeAddress(pageTable[vPage].ppn, pgOffset);
		System.arraycopy(memory, pAddr, data, buffOffset + copied, 1); //or bytesToCopy
		bytesLeft -= bytesToCopy;
		copied++;
		vPage++;
		buffOffset += bytesToCopy;
		pgOffset = 0;
	}

	return copied;
	*/
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param    vaddr    the first byte of virtual memory to write.
     * @param    data    the array containing the data to transfer.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
        return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param    vaddr    the first byte of virtual memory to write.
     * @param    data    the array containing the data to transfer.
     * @param    offset    the first byte to transfer from the array.
     * @param    length    the number of bytes to transfer from the array to
     * virtual memory.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
                                  int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

	//memoryLock.acquire();
	byte[] memory = Machine.processor().getMemory();
	int copied = 0;
	int bitPerPage = 1024;

	while(data.length > offset && length > 0){
		int addressOffset = vaddr % bitPerPage;
		int virtPageNum = vaddr / bitPerPage;

		if(virtPageNum < 0 || virtPageNum >= pageTable.length)
			break;

		TranslationEntry pageTentry = pageTable[virtPageNum];

		if(!pageTentry.valid)
			break;

		pageTentry.dirty = true;
		pageTentry.used = true;
		int physPageNum = pageTentry.ppn;
		int physAddress = physPageNum * bitPerPage + addressOffset;
		int copiedSize = Math.min(data.length - offset, Math.min(length,  bitPerPage - addressOffset));
		System.arraycopy(data, offset, memory, physAddress, copiedSize);
		
		vaddr = vaddr + copiedSize;
		offset = offset + copiedSize;
		length = length - copiedSize;
		copied = copied + copiedSize;
	}
	return copied;

	/*
	// for now, just assume that virtual addresses equal physical addresses
	if (vaddr < 0)
	    return 0;

	// changes
	int charsCopied = 0;
	int pAddr;
//	int amount = min(length, memory.length-vaddr);
	while(length > charsCopied){
		if(Processor.pageFromAddress(vaddr + charsCopied) >= numPages){
			break;
		}
		pAddr = Processor.makeAddress(pageTable[Processor.pageFromAddress(vaddr + charsCopied)].ppn, Processor.offsetFromAddress((vaddr + charsCopied)));

		System.arraycopy(data, offset  + charsCopied, memory, pAddr, 1); // offset and vaddr are swapped; length is 1 because we are reading and writing 1 byte at a time
		charsCopied++;
	}
	//memoryLock.release();
	//changes

//	int amount = Math.min(length, memory.length-vaddr);
//	System.arraycopy(data, offset, memory, vaddr, amount);

	return charsCopied;
	*/
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param    name    the name of the file containing the executable.
     * @param    args    the arguments to pass to the executable.
     * @return    <tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
        Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

        OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
        if (executable == null) {
            Lib.debug(dbgProcess, "\topen failed");
            return false;
        }

        try {
            coff = new Coff(executable);
        } catch (EOFException e) {
            executable.close();
            Lib.debug(dbgProcess, "\tcoff load failed");
            return false;
        }

        // make sure the sections are contiguous and start at page 0
        numPages = 0;
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);
            if (section.getFirstVPN() != numPages) {
                coff.close();
                Lib.debug(dbgProcess, "\tfragmented executable");
                return false;
            }
            numPages += section.getLength();
        }

        // make sure the argv array will fit in one page
        byte[][] argv = new byte[args.length][];
        int argsSize = 0;
        for (int i = 0; i < args.length; i++) {
            argv[i] = args[i].getBytes();
            // 4 bytes for argv[] pointer; then string plus one for null byte
            argsSize += 4 + argv[i].length + 1;
        }
        if (argsSize > pageSize) {
            coff.close();
            Lib.debug(dbgProcess, "\targuments too long");
            return false;
        }

        // program counter initially points at the program entry point
        initialPC = coff.getEntryPoint();

        // next comes the stack; stack pointer initially points to top of it
        numPages += stackPages;
        initialSP = numPages * pageSize;

        // and finally reserve 1 page for arguments
        numPages++;

        if (!loadSections())
            return false;

        // store arguments in last page
        int entryOffset = (numPages - 1) * pageSize;
        int stringOffset = entryOffset + args.length * 4;

        this.argc = args.length;
        this.argv = entryOffset;

        for (int i = 0; i < argv.length; i++) {
            byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
            Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
            entryOffset += 4;
            Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
                    argv[i].length);
            stringOffset += argv[i].length;
            Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[]{0}) == 1);
            stringOffset += 1;
        }

        return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return    <tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}
		memoryLock.acquire();

		pageTable = new TranslationEntry[numPages];
		physicalPageList = new int[numPages];

		//for loop to fill physicalPageList
		for(int i = 0;i<numPages; i++) {
			//physicalPageList[i] = UserKernel.availablePages.pop().intValue();
			physicalPageList[i] = UserKernel.allocatePage();
		}
		// use linked list to keep track of available pages. linked list stores integer value as index of element
		// linked list size is how many available pages there are
		// pop the page
		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
				  + " section (" + section.getLength() + " pages)");

			for (int i=0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN()+i;
				int ppn = physicalPageList[vpn];
				pageTable[vpn] = new TranslationEntry(vpn, ppn, true, section.isReadOnly(), false, false);
				section.loadPage(i, ppn);
			}
		}

		//load stack pages
		for(int s = numPages - 9; s < numPages; s++){
			pageTable[s] = new TranslationEntry(s, physicalPageList[s], true, false, false, false);
		}

		memoryLock.release();
		return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
    	for(int i = 0; i < numPages; i++){
    		if(pageTable[i] != null){
    			pageTable[i].valid = false;
    			UserKernel.deallocatePage(pageTable[i].ppn);
			}
		}
    }

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
        Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i=0; i<processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call.
     */
    private int handleHalt() {

        Machine.halt();
        Lib.assertNotReached("Machine.halt() did not halt machine!");
        return 0;
    }

    /**
     * 	-----------------------------------------------------
     *  PART 3 OF PROJECT 2 [Exit, Exec, Join]
     * 	-----------------------------------------------------
     *
     * @param status
     */
    private void handleExit(int status) {
        //CLose all open files
        for (OpenFile file : fileDescriptors.values()) {
            file.close();
        }
        //Going over the childen and removing their parents pid
        for (Map.Entry<Integer, UserProcess> entry : childProcessList.entrySet())
        {
            entry.getValue().parentpid=-1;
        }
        //unload the registers and memory used by the preocess
        this.unloadSections();

        //set the status of the process into this
        this.Pstatus = status;

        //if root process, halt the machine
        if (this.pid == 0) {
            Kernel.kernel.terminate();
        } else {
            UThread.finish();
        }
    }

    /**
     *
     * @param namePtr name of the executable file
     * @param argc number of arguments
     * @param argv array/vector of the arguments
     * @return
     */
    private int handleExec(int namePtr, int argc, int argv){
    	//Getting name of the file from name pointer
		String fileName = readVirtualMemoryString(namePtr, MAX_FILE_NAME_SIZE);

		//if no filename, or filename without ".coff" extension
        //or 0 argument. Then return -1
		if(fileName == null || !fileName.endsWith(".coff") || argc<0){
		    return -1;
        }

		//To get the address for a parameter string, we need to read
        //4 bytes from the memory and reorder them into an an integer
        //Converting bytes from this array into integer
        byte[] bytesArray = new byte[4];
		//And storing in this array
		String[] args = new String[argc];


		for(int i=0; i < argc; i++)
        {
            //Transferring data from virtual memory to bytesArray
            //Make sure its just 4
		    if(readVirtualMemory(argv +i*4, bytesArray)==4)
		    {
                //convert bytesArray to Integer using bytesToInt
                //store to bytesarray
                //read string from virtual memory
		        args[i]=readVirtualMemoryString(Lib.bytesToInt(bytesArray,0),MAX_FILE_NAME_SIZE);
		        //however, if nothings recorded, return -1
		        if(args[i]==null)
		        {
		            return -1;
                }
            }
        }
		//create a new childProcess using these data
		UserProcess childProcess = new UserProcess();
		//set this process as the parent of the new childProcess
		childProcess.parentpid = this.pid;

		//if the process is executed with given arguments
		if(childProcess.execute(fileName, args))
        {
            //insert the child's pid as key, and childProcess itself into hashmap
            childProcessList.put(childProcess.pid, childProcess);
            return childProcess.pid;
        }

		return -1;
	}

    /**
     *
     * @param pid child's Pid
     * @param status
     * @return
     */
	int handleJoin(int pid, int status){

        //if parents childProcessList contains the given Process id
        if(pid>= 0 && status >= 0 && childProcessList.containsKey(pid))
        {
            //link the Process with given Pid to this childProcess
            UserProcess childProcess = childProcessList.get(pid);

            //Join to the given childProcess
            childProcess.thread.join();
            // Remove child from parents childList to prevent subsequent joins
            childProcessList.remove(pid);
            if(writeVirtualMemory(status, Lib.bytesFromInt(childProcess.Pstatus))!=4)
            {
                return 1;
            }
            else{
                return 0;
            }
        }
        else{
            return -1;
        }

	}

    /**
     * Get a file descriptor that is not currently in use.
     *
     * @return A free file descriptor
     */
    public int getFreeFileDescriptor() {
        int fileDescriptor = 2;
        while (fileDescriptors.containsKey(fileDescriptor)) {
            if (fileDescriptor < MAX_FD) {
                fileDescriptor += 1;
            } else {
                return -1;
            }
        }
        return fileDescriptor;
    }

    /**
     * Handler for tye syscall creat
     *
     * @param namePtr the pointer to the file name string
     * @return the file descriptor, for now. TODO: return 0 if success?
     */
    private int handleCreate(int namePtr) {
        String name = readVirtualMemoryString(namePtr, MAX_FILE_NAME_SIZE);

        int fileDescriptor = getFreeFileDescriptor();

        if (fileDescriptor > MAX_FD) {
            throw new ArithmeticException("Crab! Fish Descriptor is too big!");
        }

        if (fileDescriptor < 0) {
            return -1;
        }

        OpenFile openFile = ThreadedKernel.fileSystem.open(name, true);
        if (openFile == null) {
            return -1;
        }
        fileDescriptors.put(fileDescriptor, openFile);
        return fileDescriptor;
    }

    private int handleOpen(int namePtr) {
        String name = readVirtualMemoryString(namePtr, MAX_FILE_NAME_SIZE);

        int fileDescriptor = getFreeFileDescriptor();

        if (fileDescriptor > MAX_FD) {
            throw new ArithmeticException("Crab! Fish Descriptor is too big!");
        }

        if (fileDescriptor < 0) {
            return -1;
        }

        OpenFile openFile = ThreadedKernel.fileSystem.open(name, false);
        if (openFile == null) {
            return -1;
        }
        fileDescriptors.put(fileDescriptor, openFile);
        return fileDescriptor;
    }

    /**
     * Handler for the syscall read
     *
     * @param fd file descriptor
     * @param bufferAddress pointer to the buffer
     * @param size size of the buffer
     *
     * @return numbers of byte loaded; -1 if failed
     */
    private int handleRead(int fd, int bufferAddress, int size) {

        if (!fileDescriptors.containsKey(fd)) {
            return -1;
        }

        if (size <= 0) {
            return -1;
        }

        byte[] byteBuffer = new byte[size];

        int byteRead = fileDescriptors.get(fd).read(byteBuffer, 0, size);

        if (byteRead < 0) {
            return -1;
        }

        int byteLoaded = writeVirtualMemory(bufferAddress, byteBuffer, 0, byteRead);

        if (byteLoaded < 0) {
            return -1;
        }

        // if (size > 1) {
        //     throw new ArithmeticException(String.format(
        //             "fd=%d, bufferAddr=%d, size=%d, return=%d\n" +
        //             "content=%s",
        //             fd,
        //             bufferAddress,
        //             size,
        //             byteLoaded,
        //             Arrays.toString(byteBuffer)
        //             ));
        // }

        return byteLoaded;
    }

    /**
     * Handler for the syscall write
     *
     * @param fd file descriptor
     * @param bufferAddress pointer to the buffer
     * @param size size of the buffer
     *
     * @return numbers of byte written; -1 if failed
     */
    private int handleWrite(int fd, int bufferAddress, int size) {

        if (!fileDescriptors.containsKey(fd)) {
            return -1;
        }

        if (size <= 0) {
            return -1;
        }

        byte[] byteBuffer = new byte[size];

        int byteLoaded = readVirtualMemory(bufferAddress, byteBuffer, 0, size);

        if (byteLoaded < 0) {
            return -1;
        }

        int byteWritten = fileDescriptors.get(fd).write(byteBuffer, 0, byteLoaded);

        if (byteWritten < 0) {
            return -1;
        }


        return byteWritten;
    }



    private int handleClose(int fd) {
        if (fileDescriptors.containsKey(fd)) {
            OpenFile openFile = fileDescriptors.get(fd);
            String name = openFile.getName();
            if (!UserKernel.fileSystem.remove(name)) {
                return -1;
            }
            openFile.close();
            fileDescriptors.remove(fd);
            return 0;
        } else {
            return -1;
        }
    }

    private int handleUnlink(int namePtr) {
        String name = readVirtualMemoryString(namePtr, MAX_FILE_NAME_SIZE);

        OpenFile openFile = null;

        for (OpenFile file : fileDescriptors.values()) {
            if (name.equals(file.getName())) {
                openFile = file;
            }
        }

        if (openFile == null) {
            return UserKernel.fileSystem.remove(name) ? 1 : 0;
        }
        return 0;
    }


    private static final int
            syscallHalt = 0,
            syscallExit = 1,
            syscallExec = 2,
            syscallJoin = 3,
            syscallCreate = 4,
            syscallOpen = 5,
            syscallRead = 6,
            syscallWrite = 7,
            syscallClose = 8,
            syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * </tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     * </tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     * </tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     *
     * @param    syscall    the syscall number.
     * @param    a0    the first syscall argument.
     * @param    a1    the second syscall argument.
     * @param    a2    the third syscall argument.
     * @param    a3    the fourth syscall argument.
     * @return the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
        switch (syscall) {
            case syscallHalt:
                return handleHalt();
            case syscallExit:
                handleExit(a0);
                return 0;
            case syscallExec:
                return handleExec(a0,a1,a2);
            case syscallJoin:
                return handleJoin(a0,a1);
            case syscallCreate:
                return handleCreate(a0);
            case syscallOpen:
                return handleOpen(a0);
            case syscallRead:
                return handleRead(a0, a1, a2);
            case syscallWrite:
                return handleWrite(a0, a1, a2);
            case syscallClose:
                return handleClose(a0);
            case syscallUnlink:
                return handleUnlink(a0);


            default:
                if (syscall > syscallUnlink) {
                    throw new ArithmeticException(
                            String.format(
                                    "syscall=%d, a0=%d, a1=%d, a2=%d",
                                    syscall,
                                    a0,
                                    a1,
                                    a2
                            )
                    );
                }
                // Lib.debug(dbgProcess, "Unknown syscall " + syscall);
                // Lib.assertNotReached("Unknown system call!");
        }
        return 0;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param    cause    the user exception that occurred.
     */
    public void handleException(int cause) {
        Processor processor = Machine.processor();

        switch (cause) {
            case Processor.exceptionSyscall:
                int result = handleSyscall(processor.readRegister(Processor.regV0),
                        processor.readRegister(Processor.regA0),
                        processor.readRegister(Processor.regA1),
                        processor.readRegister(Processor.regA2),
                        processor.readRegister(Processor.regA3)
                );
                processor.writeRegister(Processor.regV0, result);
                processor.advancePC();
                break;

            default:
                Lib.debug(dbgProcess, "Unexpected exception: " +
                        Processor.exceptionNames[cause]);
                Lib.assertNotReached("Unexpected exception");
        }
    }

    /**
     * The program being run by this process.
     */
    protected Coff coff;

    /**
     * This process's page table.
     */
    protected TranslationEntry[] pageTable;
    /**
     * The number of contiguous pages occupied by the program.
     */
    protected int numPages;
	protected int physicalPageList[];

    /**
     * The number of pages in the program's stack.
     */
    protected final int stackPages = 8;

    private int initialPC, initialSP;
    private int argc, argv;

    private Map<Integer, OpenFile> fileDescriptors = new HashMap<Integer, OpenFile>();

    private static final int MAX_FILE_NAME_SIZE = 256;
    private static final int MAX_FD = 16;

    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';

    //process ID
    protected int pid;
    private static int uniquepid=-1;
    protected int parentpid=-1;
    private int Pstatus;
    private UThread thread;
    //changes
	private Lock memoryLock = new Lock();
}
