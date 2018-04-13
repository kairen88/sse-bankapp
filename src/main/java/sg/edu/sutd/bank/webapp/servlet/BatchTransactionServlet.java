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

import static sg.edu.sutd.bank.webapp.servlet.ServletPaths.BATCH_TRANSACTION;
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
import sg.edu.sutd.bank.webapp.model.UserStatus;
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
@WebServlet(BATCH_TRANSACTION)
public class BatchTransactionServlet extends DefaultServlet {
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
		if (BATCH_TRANSACTION_ACTION.endsWith(actionType)) {
			batchTransaction(req, resp);
		}
	}

	private void batchTransaction(HttpServletRequest req, HttpServletResponse resp)
			throws IOException, ServletException {

		// Verify the content type
		String contentType = req.getContentType();
		if ((contentType.indexOf("multipart/form-data") >= 0)) {
			String path = "D:\\tmp";
			File uploadFile = uploadFile(req, path);
			
			try {
				ArrayList<String[]> batchTransactions = loadBatchFile(uploadFile);
	
				// submit transactions
				if (batchTransactions.isEmpty()) {
					sendError(req, "Batch file is empty");
				}
			
				User user = new User(getUserId(req));
				// validate batch transaction
				Double totalAmount = 0.0;
				for (String[] transDetails : batchTransactions)
					totalAmount += Double.valueOf(transDetails[1]);

				BigDecimal batchTotalAmt = new BigDecimal(totalAmount);
				if (batchTotalAmt.compareTo(clientAcctDAO.load(user.getId()).getAmount()) > 0) {
					throw new ServiceException(
							new Throwable("Total amount transferred is more than current account balance"));
				}
				// create the transaction
				for (String[] transDetails : batchTransactions) {

					BigDecimal transAmt = new BigDecimal(Double.valueOf(transDetails[1]));
					ClientTransaction clientTransaction = new ClientTransaction();
					clientTransaction.setUser(user);
					clientTransaction.setTransCode(transDetails[0]);
					clientTransaction.setAmount(transAmt);
					clientTransaction.setToAccountNum(transDetails[2]);

					if (isTransValid(clientTransaction)) {
						clientTransactionDAO.create(clientTransaction);
						// set transaction code status to 1 (1 = used, 0 = unused)
						transCodeDAO.update(transDetails[0], 1);
						
						//if transaction amount < 10.0 auto approve and transfer
						if(transAmt.compareTo(new BigDecimal(10.0)) < 0)
						{
							ClientTransaction trans = clientTransactionDAO.load(transDetails[0]);
							if(trans != null)
							{
								trans.setStatus(TransactionStatus.APPROVED);
								List<ClientTransaction> transactions = new ArrayList<ClientTransaction>();
								transactions.add(trans);
								clientTransactionDAO.updateDecision(transactions); 
								User receiver = userDAO.loadUser(transDetails[2]);
								if(receiver != null)
									clientAcctDAO.transferAmount(clientTransaction, receiver);		
								else
									throw new ServiceException(new Throwable("Receiver is invalid"));
							}else
							{
								throw new ServiceException(new Throwable("Transaction code is invalid"));
							}
						}
						
					}
				}
			} catch (ServiceException e) {
				sendError(req, e.getMessage());
				forward(req, resp);
			}
			redirect(resp, ServletPaths.CLIENT_DASHBOARD_PAGE);
		}
	}

	private File uploadFile(HttpServletRequest req, String path) throws IOException, ServletException {
		final Part filePart = req.getPart("file");
		String fileName = null;
		// get filename
		for (String content : filePart.getHeader("content-disposition").split(";")) {
			if (content.trim().startsWith("filename")) {
				fileName = content.substring(content.indexOf('=') + 1).trim().replace("\"", "");
			}
		}
		OutputStream out = null;
		InputStream filecontent = null;
		File uploadFile = null;

		try {
			// upload file
			uploadFile = new File(path + File.separator + fileName);
			out = new FileOutputStream(uploadFile);
			filecontent = filePart.getInputStream();

			int read = 0;
			final byte[] bytes = new byte[1024];

			while ((read = filecontent.read(bytes)) != -1) {
				out.write(bytes, 0, read);
			}
		} catch (FileNotFoundException fne) {
			System.out.println("FILE NOT FOUND");
		} finally {
			if (out != null) {
				out.close();
			}
			if (filecontent != null) {
				filecontent.close();
			}
		}
		return uploadFile;
	}

	private ArrayList<String[]> loadBatchFile(File uploadFile) throws ServiceException {
		String contentType;
		ArrayList<String[]> batchTransactions = new ArrayList<String[]>();
		if (uploadFile != null) {
			// load the csv file
			Path filePath = uploadFile.toPath();
			BufferedReader br = null;
			try {
				contentType = Files.probeContentType(filePath);
				if ("text/csv".equals(contentType) || "text/plain".equals(contentType)) {
					br = new BufferedReader(new FileReader(uploadFile));
					String line = "";
	
					while ((line = br.readLine()) != null) {
						String[] transInputAry = line.split(",");
						String transCode = sanitizeInputStr(transInputAry[0]);
						String amount = sanitizeInputStr(transInputAry[1]);
						String recpiantId = sanitizeInputStr(StringUtils.sanitizeString(transInputAry[2]));
	
						batchTransactions.add(new String[] { transCode, amount, recpiantId });
				}
			}
			}catch (Exception e) {
				// TODO: handle exception
				e.printStackTrace();
			}finally {
				try {br.close();} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return batchTransactions;
	}

	private String sanitizeInputStr(String inputString) {
		return inputString.trim();
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
		//receiver account is approved 
		User receiverUsr = userDAO.loadUser(clientTrans.getToAccountNum());
		if(receiverUsr.getStatus().compareTo(UserStatus.APPROVED) != 0) {
			throw new ServiceException(new Throwable("User account is not approved"));
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
