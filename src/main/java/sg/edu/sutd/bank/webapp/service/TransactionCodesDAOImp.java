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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import sg.edu.sutd.bank.webapp.commons.ServiceException;
import sg.edu.sutd.bank.webapp.model.ClientTransaction;
import sg.edu.sutd.bank.webapp.model.TransactionStatus;
import sg.edu.sutd.bank.webapp.model.User;

public class TransactionCodesDAOImp extends AbstractDAOImpl implements TransactionCodesDAO {

	@Override
	public void create(List<String> codes, int userId) throws ServiceException {
		Connection conn = connectDB();
		PreparedStatement ps;
		try {
			StringBuilder query = new StringBuilder();
			query.append("INSERT INTO transaction_code(code, user_id, used)"
					+ " VALUES ");
			int idx = 1;
			for (int i = 0; i < codes.size(); i++) {
				query.append("(?, ?, ?)");
				if (i < (codes.size() - 1)) {
					query.append(", ");
				}
			}
			ps = prepareStmt(conn, query.toString());
			for (int i = 0; i < codes.size(); i++) {
				ps.setString(idx++, codes.get(i));
				ps.setInt(idx++, userId);
				ps.setBoolean(idx++, false);
			}
			int rowNum = ps.executeUpdate();
			if (rowNum == 0) {
				throw new SQLException("Update failed, no rows affected!");
			}
		} catch (SQLException e) {
			throw ServiceException.wrap(e);
		}
	}
	
	@Override
	public int loadStatus(String transCode) throws ServiceException {
		Connection conn = connectDB();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			ps = conn.prepareStatement(
					"SELECT used FROM transaction_code WHERE code = ?"); //SHOULD NOT USE SELECT *
			int idx = 1;
			ps.setString(1, transCode);
			rs = ps.executeQuery();
			if(rs.next())
			{
				return rs.getInt("used");
			}
		} catch (SQLException e) {
			throw ServiceException.wrap(e);
		} finally {
			closeDb(conn, ps, rs);
		}
		return -1;
	}
	
	@Override
	public int loadTransCodeUserId(String transCodeStr) throws ServiceException {
		Connection conn = connectDB();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			ps = conn.prepareStatement(
					"SELECT user_id FROM transaction_code WHERE code = ?"); //SHOULD NOT USE SELECT *
			int idx = 1;
			ps.setString(1, transCodeStr);
			rs = ps.executeQuery();
			if(rs.next())
			{
				System.out.println(rs.getInt("user_id"));
				return rs.getInt("user_id");
			}
		} catch (SQLException e) {
			throw ServiceException.wrap(e);
		} finally {
			closeDb(conn, ps, rs);
		}
		return -1;
	}
	
	@Override
	public void update(String code, int status) throws ServiceException {
		Connection conn = connectDB();
		try {
			PreparedStatement ps = conn.prepareStatement("UPDATE transaction_code SET used = ? WHERE code = ?");
			ps.setInt(1, status);
			ps.setString(2, code);		
		
			int rowNum = ps.executeUpdate();
			if (rowNum == 0) {
				throw new SQLException("Update failed, no rows affected!");
			}
		} catch (SQLException e) {
			throw ServiceException.wrap(e);
		}
	}

}
