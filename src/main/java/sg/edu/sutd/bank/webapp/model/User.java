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

package sg.edu.sutd.bank.webapp.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class User extends AbstractIdEntity {
	private String userName;
	private String password;
	private UserStatus status;
	private String salt;

	public User() {
	}

	public User(int userId) {
		setId(userId);
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public UserStatus getStatus() {
		return status;
	}

	public void setStatus(UserStatus status) {
		this.status = status;
	}
	
	public void setSalt(String salt) {
		this.salt = salt;
	}
	
	public String getSalt() {
		return salt;
	}

	public void setStatus(String str) {
		UserStatus status = null;
		if (str != null) {
			status = UserStatus.valueOf(str);
		}
		setStatus(status);
	}

	public String hashPassword(String passwordToHash, String salt) {
		String generatedPassword = null;
		if(salt==null)
			return null;
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(salt.getBytes());
			byte[] bytes = md.digest(passwordToHash.getBytes());
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < bytes.length; i++) {
				sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
			}
			generatedPassword = sb.toString();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return generatedPassword;
	}

	public String generateSalt() 
    {
		byte[] salt = null;
		try {
        SecureRandom sr;
		sr = SecureRandom.getInstance("SHA1PRNG");
        salt = new byte[16];
        sr.nextBytes(salt);
        } catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return salt.toString();
    }

}
