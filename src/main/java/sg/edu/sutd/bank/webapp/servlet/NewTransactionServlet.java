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

package sg.edu.sutd.bank.webapp.servlet;

import static sg.edu.sutd.bank.webapp.servlet.ServletPaths.NEW_TRANSACTION;

import java.io.IOException;
import java.math.BigDecimal;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import sg.edu.sutd.bank.webapp.commons.ServiceException;
import sg.edu.sutd.bank.webapp.model.ClientInfo;
import sg.edu.sutd.bank.webapp.model.ClientTransaction;
import sg.edu.sutd.bank.webapp.model.User;
import sg.edu.sutd.bank.webapp.service.ClientAccountDAO;
import sg.edu.sutd.bank.webapp.service.ClientAccountDAOImpl;
import sg.edu.sutd.bank.webapp.service.ClientInfoDAO;
import sg.edu.sutd.bank.webapp.service.ClientInfoDAOImpl;
import sg.edu.sutd.bank.webapp.service.ClientTransactionDAO;
import sg.edu.sutd.bank.webapp.service.ClientTransactionDAOImpl;
import sg.edu.sutd.bank.webapp.service.TransactionCodesDAO;
import sg.edu.sutd.bank.webapp.service.TransactionCodesDAOImp;

@WebServlet(NEW_TRANSACTION)
public class NewTransactionServlet extends DefaultServlet {
	private static final long serialVersionUID = 1L;
	private ClientTransactionDAO clientTransactionDAO = new ClientTransactionDAOImpl();
	private ClientInfoDAO clientInfoDAO = new ClientInfoDAOImpl();
	private TransactionCodesDAO transCodeDAO = new TransactionCodesDAOImp();
	private ClientAccountDAO clientAcctDAO = new ClientAccountDAOImpl();
	
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			ClientTransaction clientTransaction = new ClientTransaction();
			User user = new User(getUserId(req));
			clientTransaction.setUser(user);
			clientTransaction.setAmount(new BigDecimal(req.getParameter("amount")));
			clientTransaction.setTransCode(req.getParameter("transcode"));
			clientTransaction.setToAccountNum(req.getParameter("toAccountNum"));
			
			//check if transaction is valid
			if(isTransValid(clientTransaction))
			{
				clientTransactionDAO.create(clientTransaction);
				redirect(resp, ServletPaths.CLIENT_DASHBOARD_PAGE);
			}
		} catch (ServiceException e) {
			sendError(req, e.getMessage());
			forward(req, resp);
		}
	}
	
	private boolean isTransValid(ClientTransaction clientTrans) throws ServiceException{
		//transfer amount is > 0
		if(clientTrans.getAmount().compareTo(BigDecimal.ZERO) < 0)
		{
			throw new ServiceException(new Throwable("Transfer amount must be greater than zero"));
		}
		//transfer is made to a different account
		ClientInfo clientInfo = clientInfoDAO.loadAccountInfo(clientTrans.getToAccountNum());
		if(clientTrans.getUser().getId() == clientInfo.getUser().getId())
		{
			throw new ServiceException(new Throwable("Transfer must be made to a different account"));
		}
		//transfer Code is valid and has not been used and it belongs to the user
		if(transCodeDAO.loadStatus(clientTrans.getTransCode()) != 0 ||
				transCodeDAO.loadTransCodeUserId(clientTrans.getTransCode()) != clientTrans.getUser().getId())
		{
			System.out.println(transCodeDAO.loadTransCodeUserId(clientTrans.getTransCode()) +" "+ clientTrans.getUser().getId());
			throw new ServiceException(new Throwable("Transaction Code is not valid"));
		}
		//amount transferred is less than or equal to the current amount in this account
		if(clientTrans.getAmount().compareTo(clientAcctDAO.load(clientTrans.getUser().getId()).getAmount()) > 0)
		{
			throw new ServiceException(new Throwable("Amount transferred is more than current account balance"));
		}
		return true;
	}
}
