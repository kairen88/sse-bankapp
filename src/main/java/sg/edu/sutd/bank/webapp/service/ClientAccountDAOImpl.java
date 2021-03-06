/*
 * Copyright 2017 SUTD Licensed under the
	Educational Community License, Version 2.0 (the "License"); you may
	not use this file except in compliance with the License. You may
	obtain a copy of the License at

https://opensource.org/licenses/ECL-2.0

	Unless required by applicable law or agreed to in writing,
	software distributed under the License is distributed on an "AS IS"
	BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
	or implied. See the License for the specific language governing
	permissions and limitations under the License.
 */

package sg.edu.sutd.bank.webapp.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import sg.edu.sutd.bank.webapp.commons.Locks;
import sg.edu.sutd.bank.webapp.commons.ServiceException;
import sg.edu.sutd.bank.webapp.model.ClientAccount;
import sg.edu.sutd.bank.webapp.model.ClientTransaction;
import sg.edu.sutd.bank.webapp.model.User;

public class ClientAccountDAOImpl extends AbstractDAOImpl implements ClientAccountDAO {


	@Override
	public void create(ClientAccount clientAccount) throws ServiceException {
		Connection conn = connectDB();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			ps = prepareStmt(conn, "INSERT INTO client_account(user_id, amount) VALUES(?,?)");
			int idx = 1;
			ps.setInt(idx++, clientAccount.getUser().getId());
			ps.setBigDecimal(idx++, clientAccount.getAmount());
			executeInsert(clientAccount, ps);
		} catch (SQLException e) {
			throw ServiceException.wrap(e);
		} finally {
			closeDb(conn, ps, rs);
		}
	}

	@Override
	synchronized public void update(ClientAccount clientAccount) throws ServiceException {
		Connection conn = connectDB();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			ps = prepareStmt(conn, "UPDATE client_account SET amount = ? WHERE user_id = ?");
			int idx = 1;
			ps.setBigDecimal(idx++, clientAccount.getAmount());
			ps.setInt(idx++, clientAccount.getUser().getId());
			executeUpdate(ps);
		} catch (SQLException e) {
			throw ServiceException.wrap(e);
		} finally {
			closeDb(conn, ps, rs);
		}
	}
	
	@Override
	public ClientAccount load(int userId) throws ServiceException {
		Connection conn = connectDB();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			ps = prepareStmt(conn, "SELECT * FROM client_account WHERE user_id = ?");
			ps.setInt(1, userId);
			rs = ps.executeQuery();
			
			ClientAccount acct = null;
			if (rs.next()) {
				acct = new ClientAccount();
				User usr = new User();
				usr.setId(rs.getInt("user_id"));
				acct.setUser(usr);
				acct.setAmount(rs.getBigDecimal("amount"));
			}
			return acct;
			
		} catch (SQLException e) {
			throw ServiceException.wrap(e);
		} finally {
			closeDb(conn, ps, rs);
		}
	}
	
	@Override
	public ArrayList<ClientAccount> loadAll() throws ServiceException {
		Connection conn = connectDB();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			ps = prepareStmt(conn, "SELECT * FROM client_account");
			rs = ps.executeQuery();
			
			ArrayList<ClientAccount> accountList = new ArrayList<>();
			ClientAccount acct = null;
			while (rs.next()) {
				acct = new ClientAccount();
				User usr = new User();
				usr.setId(rs.getInt("user_id"));
				acct.setUser(usr);
				acct.setAmount(rs.getBigDecimal("amount"));
				accountList.add(acct);
			}
			return accountList;
			
		} catch (SQLException e) {
			throw ServiceException.wrap(e);
		} finally {
			closeDb(conn, ps, rs);
		}
	}
	
	@Override
	public void transferAmount (ClientTransaction clientTrans, User receiver) throws Exception {	
		Object fromAcctLock = Locks.accountLocks.get(clientTrans.getUser().getId());
		Object toAcctLock = Locks.accountLocks.get(receiver.getId());
		
		int fromHash = System.identityHashCode(fromAcctLock); 
		int toHash = System.identityHashCode(toAcctLock);
		
		if (fromHash < toHash) {
			synchronized (fromAcctLock) {
				synchronized (toAcctLock) {
					transfer(clientTrans, receiver);
				}
			}
		}
		else if (fromHash > toHash) {
			synchronized (toAcctLock) {
				synchronized (fromAcctLock) {
					transfer(clientTrans, receiver);
				}
			}			
		}
		else {
			synchronized (Locks.accountTieLock) {
				synchronized (fromAcctLock) {
					synchronized (toAcctLock) {
						transfer(clientTrans, receiver);
					}
				}
			}
		}
	}
	
	private void transfer(ClientTransaction clientTrans, User receiver) throws ServiceException {
		
		synchronized(Locks.transferLock) {
			//get sender account info
			ClientAccount senderAcct = load(clientTrans.getUser().getId());
			//debit sender account
			BigDecimal senderAmt = senderAcct.getAmount();
			senderAmt = senderAmt.subtract(clientTrans.getAmount());
			senderAcct.setAmount(senderAmt);
			update(senderAcct);
			
			//get receiver account info						
			ClientAccount recAcct = load(receiver.getId());
			//credit receiver account
			BigDecimal recAmt = recAcct.getAmount();
			recAmt = recAmt.add(clientTrans.getAmount());
			recAcct.setAmount(recAmt);
			update(recAcct); 
		}
	}

}
