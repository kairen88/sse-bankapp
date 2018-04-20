package sg.edu.sutd.bank.webapp.commons;

import java.util.HashMap;

public class Locks {
	
	public static final Object transactionLock = new Object();
//	public static final Object accountLock = new Object();
	public static final Object transferLock = new Object();
	public static final Object accountTieLock = new Object();
	public static final HashMap<Integer, Object>accountLocks = new HashMap<>();
}
