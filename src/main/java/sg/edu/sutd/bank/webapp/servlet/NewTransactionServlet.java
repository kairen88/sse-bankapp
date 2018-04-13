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
import static sg.edu.sutd.bank.webapp.servlet.ServletPaths.STAFF_DASHBOARD_PAGE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import sg.edu.sutd.bank.webapp.commons.ServiceException;
import sg.edu.sutd.bank.webapp.commons.StringUtils;
import sg.edu.sutd.bank.webapp.model.ClientInfo;
import sg.edu.sutd.bank.webapp.model.ClientTransaction;
import sg.edu.sutd.bank.webapp.model.TransactionStatus;
import sg.edu.sutd.bank.webapp.model.User;
import sg.edu.sutd.bank.webapp.service.ClientAccountDAO;
import sg.edu.sutd.bank.webapp.service.ClientAccountDAOImpl;
import sg.edu.sutd.bank.webapp.service.ClientInfoDAO;
import sg.edu.sutd.bank.webapp.service.ClientInfoDAOImpl;
import sg.edu.sutd.bank.webapp.service.ClientTransactionDAO;
import sg.edu.sutd.bank.webapp.service.ClientTransactionDAOImpl;
import sg.edu.sutd.bank.webapp.service.TransactionCodesDAO;
import sg.edu.sutd.bank.webapp.service.TransactionCodesDAOImp;
import sg.edu.sutd.bank.webapp.service.UserDAO;
import sg.edu.sutd.bank.webapp.service.UserDAOImpl;

@MultipartConfig
@WebServlet(NEW_TRANSACTION)
public class NewTransactionServlet extends DefaultServlet {
	private static final long serialVersionUID = 1L;
	public static final String BATCH_TRANSACTION_ACTION = "batchTransactionAction";
	public static final String NEW_TRANSACTION_ACTION = "newTransactionAction";
	private ClientTransactionDAO clientTransactionDAO = new ClientTransactionDAOImpl();
	private ClientInfoDAO clientInfoDAO = new ClientInfoDAOImpl();
	private TransactionCodesDAO transCodeDAO = new TransactionCodesDAOImp();
	private ClientAccountDAO clientAcctDAO = new ClientAccountDAOImpl();
	private UserDAO userDAO = new UserDAOImpl();

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String actionType = req.getParameter("actionType");
		if (NEW_TRANSACTION_ACTION.endsWith(actionType)) {
			newTransaction(req, resp);
		} 
	}

	private void newTransaction(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
		try {
			ClientTransaction clientTransaction = new ClientTransaction();
			User user = new User(getUserId(req));
			clientTransaction.setUser(user);
			clientTransaction.setAmount(new BigDecimal(req.getParameter("amount")));
			clientTransaction.setTransCode(req.getParameter("transcode"));
			clientTransaction.setToAccountNum(StringUtils.sanitizeString(req.getParameter("toAccountNum")));

			// check if transaction is valid
			if (isTransValid(clientTransaction)) {
				clientTransactionDAO.create(clientTransaction);
				//set transaction code status to 1 (1 = used, 0 = unused)
				transCodeDAO.update(req.getParameter("transcode"), 1);
				
				BigDecimal transAmt = new BigDecimal(req.getParameter("amount"));
				//if transaction amount < 10.0 auto approve and transfer
				if(transAmt.compareTo(new BigDecimal(10.0)) < 0)
				{	
					ClientTransaction trans = clientTransactionDAO.load(req.getParameter("transcode"));
					if(trans != null)
					{
						trans.setStatus(TransactionStatus.APPROVED);
						List<ClientTransaction> transactions = new ArrayList<ClientTransaction>();
						transactions.add(trans);
						clientTransactionDAO.updateDecision(transactions); 
						User receiver = userDAO.loadUser(req.getParameter("toAccountNum"));
						if(receiver != null)
							clientAcctDAO.transferAmount(clientTransaction, receiver);
						else
							throw new ServiceException(new Throwable("Recceiver is invalid"));
					}else {
						throw new ServiceException(new Throwable("Transaction code is invalid"));
					}
				}
				
				
				
				redirect(resp, ServletPaths.CLIENT_DASHBOARD_PAGE);
			}
		} catch (ServiceException e) {
			sendError(req, e.getMessage());
			forward(req, resp);
		}
	}

	private boolean isTransValid(ClientTransaction clientTrans) throws ServiceException {
		// transfer amount is > 0
		if (clientTrans.getAmount().compareTo(BigDecimal.ZERO) < 0) {
			throw new ServiceException(new Throwable("Transfer amount must be greater than zero"));
		}
		// transfer is made to a different account
		ClientInfo clientInfo = clientInfoDAO.loadAccountInfo(clientTrans.getToAccountNum());
		if (clientTrans.getUser().getId() == clientInfo.getUser().getId()) {
			throw new ServiceException(new Throwable("Transfer must be made to a different account"));
		}
		// transfer Code is valid and has not been used and it belongs to the user
		if (transCodeDAO.loadStatus(clientTrans.getTransCode()) != 0
				|| transCodeDAO.loadTransCodeUserId(clientTrans.getTransCode()) != clientTrans.getUser().getId()) {
			System.out.println(
					transCodeDAO.loadTransCodeUserId(clientTrans.getTransCode()) + " " + clientTrans.getUser().getId());
			throw new ServiceException(new Throwable("Transaction Code is not valid"));
		}
		// amount transferred is less than or equal to the current amount in this
		// account
		if (clientTrans.getAmount().compareTo(clientAcctDAO.load(clientTrans.getUser().getId()).getAmount()) > 0) {
			throw new ServiceException(new Throwable("Amount transferred is more than current account balance"));
		}
		return true;
	}
}
