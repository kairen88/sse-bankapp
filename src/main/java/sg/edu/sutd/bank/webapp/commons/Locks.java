package sg.edu.sutd.bank.webapp.commons;

public class Locks {
	
	public static final Object transactionLock = new Object();
	public static final Object accountLock = new Object();
	public static final Object transferLock = new Object();
}
