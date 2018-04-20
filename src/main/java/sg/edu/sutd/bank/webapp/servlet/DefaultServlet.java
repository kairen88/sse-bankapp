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

import java.io.IOException;
import java.util.ArrayList;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import sg.edu.sutd.bank.webapp.commons.Locks;
import sg.edu.sutd.bank.webapp.commons.ServiceException;
import sg.edu.sutd.bank.webapp.model.ClientAccount;
import sg.edu.sutd.bank.webapp.service.ClientAccountDAO;
import sg.edu.sutd.bank.webapp.service.ClientAccountDAOImpl;


@WebServlet("/")
public class DefaultServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private ClientAccountDAO clientAcctDAO = new ClientAccountDAOImpl();
	
	public DefaultServlet() {
		try {
			ArrayList<ClientAccount> accountList = clientAcctDAO.loadAll();
			for(ClientAccount acct : accountList)
			{
				Locks.accountLocks.put(acct.getUser().getId(), new Object());
			}
		} catch (ServiceException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		if (ServletPaths.LOGOUT.equals(req.getServletPath())) {
			logout(req);
			resp.sendRedirect(getRedirectPath(ServletPaths.LOGIN));
		} else if (req.getServletPath().startsWith("/resources/")) {
			forward(req, resp);
		} else {
			forward(req, resp);
		}
	}

	public void forward(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		forward(req.getServletPath(), req, resp);
	}
	
	public void sendError(HttpServletRequest req, String msg) {
		req.getSession().setAttribute("req_error", msg);
	}
	
	public int getUserId(HttpServletRequest req) {
		return (int) req.getSession().getAttribute("user_id");
	}
	
	public void setUserId(HttpServletRequest req, Integer userId) {
		req.getSession(false).setAttribute("user_id", userId);
	}
	
	public void sendMsg(HttpServletRequest req, String msg) {
		req.getSession().setAttribute("req_msg", msg);
	}
	
	private void logout(HttpServletRequest req) throws ServletException {
		req.logout();
		HttpSession session = req.getSession(false);
		if (session != null) {
			session.setAttribute("authenticatedUser", null);
		}
	}
	
	public void forward(String path, HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		RequestDispatcher view = req.getRequestDispatcher(getPath(path));
		if(view != null)
			view.forward(req, resp);
	}
	
	public void redirect(HttpServletResponse resp, String templage) throws IOException {
		resp.sendRedirect(getRedirectPath(templage));
	}

	public String getPath(String template) {
		return "WEB-INF/jsp" + template + ".jsp";
	}
	
	public String getRedirectPath(String template) {
		return "/sutdbank" + template;
	}
}
