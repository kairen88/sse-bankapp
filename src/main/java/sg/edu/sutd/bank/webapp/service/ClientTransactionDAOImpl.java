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
import java.util.List;

import sg.edu.sutd.bank.webapp.commons.ServiceException;
import sg.edu.sutd.bank.webapp.model.ClientTransaction;
import sg.edu.sutd.bank.webapp.model.TransactionStatus;
import sg.edu.sutd.bank.webapp.model.User;

public class ClientTransactionDAOImpl extends AbstractDAOImpl implements ClientTransactionDAO {

	@Override
	public void create(ClientTransaction clientTransaction) throws ServiceException {
		Connection conn = connectDB();
		PreparedStatement ps;
		try {
			ps = prepareStmt(conn, "INSERT INTO client_transaction(trans_code, amount, to_account_num, user_id)"
					+ " VALUES(?,?,?,?)");
			int idx = 1;
			ps.setString(idx++, clientTransaction.getTransCode());
			ps.setBigDecimal(idx++, clientTransaction.getAmount());
			ps.setString(idx++, clientTransaction.getToAccountNum());
			ps.setInt(idx++, clientTransaction.getUser().getId());
			executeInsert(clientTransaction, ps);
		} catch (SQLException e) {
			throw ServiceException.wrap(e);
		}
	}

	@Override
	public List<ClientTransaction> load(User user) throws ServiceException {
		Connection conn = connectDB();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			ps = conn.prepareStatement(
					"SELECT * FROM client_transaction WHERE user_id = ?");
			int idx = 1;
			ps.setInt(idx++, user.getId());
			rs = ps.executeQuery();
			List<ClientTransaction> transactions = new ArrayList<ClientTransaction>();
			while (rs.next()) {
				ClientTransaction trans = new ClientTransaction();
				trans.setId(rs.getInt("id"));
				trans.setUser(user);
				trans.setAmount(rs.getBigDecimal("amount"));
				trans.setDateTime(rs.getDate("datetime"));
				trans.setStatus(TransactionStatus.of(rs.getString("status")));
				trans.setTransCode(rs.getString("trans_code"));
				trans.setToAccountNum(rs.getString("to_account_num"));
				transactions.add(trans);
			}
			return transactions;
		} catch (SQLException e) {
			throw ServiceException.wrap(e);
		} finally {
			closeDb(conn, ps, rs);
		}
	}

	@Override
	public List<ClientTransaction> loadWaitingList() throws ServiceException {
		Connection conn = connectDB();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			ps = conn.prepareStatement(
					"SELECT * FROM client_transaction WHERE status is null");
			rs = ps.executeQuery();
			List<ClientTransaction> transactions = new ArrayList<ClientTransaction>();
			while (rs.next()) {
				ClientTransaction trans = new ClientTransaction();
				trans.setId(rs.getInt("id"));
				User user = new User(rs.getInt("user_id"));
				trans.setUser(user);
				trans.setAmount(rs.getBigDecimal("amount"));
				trans.setDateTime(rs.getDate("datetime"));
				trans.setTransCode(rs.getString("trans_code"));
				trans.setToAccountNum(rs.getString("to_account_num"));
				transactions.add(trans);
			}
			return transactions;
		} catch (SQLException e) {
			throw ServiceException.wrap(e);
		} finally {
			closeDb(conn, ps, rs);
		}
	}

	@Override
	public void updateDecision(List<ClientTransaction> transactions) throws ServiceException {
		StringBuilder query = new StringBuilder("UPDATE client_transaction SET status = Case id ");
		for (ClientTransaction trans : transactions) {
			query.append(String.format("WHEN %d THEN '%s' ", trans.getId(), trans.getStatus().name()));
		}
		query.append("ELSE status ")
			.append("END ")
			.append("WHERE id IN(");
		for (int i = 0; i < transactions.size(); i++) {
			query.append(transactions.get(i).getId());
			if (i < transactions.size() - 1) {
				query.append(", ");
			}
		}
		query.append(");");
		Connection conn = connectDB();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			ps = prepareStmt(conn, query.toString());
			int rowNum = ps.executeUpdate();
			if (rowNum == 0) {
				throw new SQLException("Update failed, no rows affected!");
			}
			
		} catch (SQLException e) {
			throw ServiceException.wrap(e);
		} finally {
			closeDb(conn, ps, rs);
		}
	}
	
	@Override
	public void initiateTransaction(List<ClientTransaction> transactions) throws ServiceException {

		//get all approved transactions
		StringBuilder getApprTransQuery = new StringBuilder("SELECT trans_code, status, amount, user_id, to_account_num FROM client_transaction WHERE status='APPROVED';");
		
		Connection conn = connectDB();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			ps = conn.prepareStatement(getApprTransQuery.toString());
			rs = ps.executeQuery();
			
			ArrayList<ClientTransaction> aprvTransList = new ArrayList<ClientTransaction>();
			
			while (rs.next()) {
				ClientTransaction ct = new ClientTransaction();
				ct.setTransCode(rs.getString("trans_code"));
				ct.setStatus(TransactionStatus.of(rs.getString("status")));
				ct.setAmount(rs.getBigDecimal("amount"));
				ct.setUser(new User(rs.getInt("user_id")));
				ct.setToAccountNum(rs.getString("to_account_num"));
				aprvTransList.add(ct);
				rs.next();
			}
			
			//for each transaction
			for(ClientTransaction trans : aprvTransList)
			{
				//read amt from sender acct
				ps = conn.prepareStatement("SELECT amount FROM client_account WHERE user_id = ?");
				ps.setInt(1,  trans.getUser().getId());
				rs = ps.executeQuery();
				BigDecimal senderAcctAmount = null;
				if (rs.next()) {
					senderAcctAmount = rs.getBigDecimal("amount");
				}
				//debit from sender acct
				if(senderAcctAmount != null) {
					ps = conn.prepareStatement("UPDATE client_account SET amount = ? WHERE user_id = ?");
					ps.setBigDecimal(1,  senderAcctAmount.subtract(trans.getAmount()));
					ps.setInt(2,  trans.getUser().getId());
					ps.executeUpdate();
				}
				
				//read amt from receiver acct
				ps = conn.prepareStatement("SELECT ca.amount, u.id FROM client_account ca, user u WHERE u.id = ca.user_id AND u.user_name = ?");
				ps.setString(1,  trans.getToAccountNum());
				rs = ps.executeQuery();
				BigDecimal recieverAcctAmount = null;
				int userId = -1;
				if (rs.next()) {
					recieverAcctAmount = rs.getBigDecimal("amount");
					userId = rs.getInt("id");
				}
				
				//credit receiver acct
				if(recieverAcctAmount != null && userId != -1) {
					ps = conn.prepareStatement("UPDATE client_account SET amount = ? WHERE user_id = ?");
					ps.setBigDecimal(1,  recieverAcctAmount.add(trans.getAmount()));
					ps.setInt(2,  userId);
					ps.executeUpdate();
				}
			}
		} catch (SQLException e) {
			throw ServiceException.wrap(e);
		} finally {
			closeDb(conn, ps, rs);
		}
		
//		StringBuilder query = new StringBuilder("UPDATE client_transaction SET status = Case id ");
//		for (ClientTransaction trans : transactions) {
//			System.out.println(trans.getId() + " " + trans.getStatus().name() + " " + trans.getToAccountNum() + " " + trans.getAmount());
//			query.append(String.format("WHEN %d THEN '%s' ", trans.getId(), trans.getStatus().name()));
//		}
//		query.append("ELSE status ")
//			.append("END ")
//			.append("WHERE id IN(");
//		for (int i = 0; i < transactions.size(); i++) {
//			query.append(transactions.get(i).getId());
//			if (i < transactions.size() - 1) {
//				query.append(", ");
//			}
//		}
//		query.append(");");
//		Connection conn = connectDB();
//		PreparedStatement ps = null;
//		ResultSet rs = null;
//		try {
//			ps = prepareStmt(conn, query.toString());
//			int rowNum = ps.executeUpdate();
//			if (rowNum == 0) {
//				throw new SQLException("Update failed, no rows affected!");
//			}
		
		
		
		
		
		
		
//			StringBuilder query2 = new StringBuilder("UPDATE client_account SET amount = Case user_id ");
//	
//	
//			StringBuilder query3 = new StringBuilder("SELECT user_id, u.id, amount FROM client_transaction ct, user u WHERE to_account_num = u.user_name AND ct.id IN (");
//			for (int i = 0; i < transactions.size(); i++) {
//				query3.append(transactions.get(i).getId());
//				if (i < transactions.size() - 1) {
//					query3.append(", ");
//				}
//			}
//			query3.append(");");
//			
//			ps = conn.prepareStatement(query3.toString());
//			rs = ps.executeQuery();
//			
//			ArrayList<Integer> usrIdList = new ArrayList<Integer>();
//			
//			if (rs.next()) {
//				query2.append(String.format("WHEN %d THEN amount + %s ", rs.getInt("id"), rs.getInt("amount")));
//				usrIdList.add(rs.getInt("id"));
//			}
//
//			
//			
//			query2.append("ELSE amount ")
//				.append("END ")
//				.append("WHERE user_id IN(");
//			for (int i = 0; i < usrIdList.size(); i++) {
//				query2.append(usrIdList.get(i));
//				if (i < usrIdList.size() - 1) {
//					query2.append(", ");
//				}
//			}
//			query2.append(");");
//			conn = connectDB();
//			ps = null;
//			rs = null;
//			
//			ps = prepareStmt(conn, query2.toString());
//			rowNum = ps.executeUpdate();
//			if (rowNum == 0) {
//				throw new SQLException("Update failed, no rows affected!");
//			}
//			
//		} catch (SQLException e) {
//			throw ServiceException.wrap(e);
//		} finally {
//			closeDb(conn, ps, rs);
//		}
	}

}
