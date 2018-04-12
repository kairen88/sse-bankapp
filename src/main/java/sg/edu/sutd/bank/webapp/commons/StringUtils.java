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

package sg.edu.sutd.bank.webapp.commons;

import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtils {
	private StringUtils() {}
	
	public static String join(List<String> vals, String separator) {
		if (vals == null || vals.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < vals.size(); i++) {
			sb.append(vals.get(i));
			if (i < (vals.size() - 1)) {
				sb.append(separator);
			}
		}
		return sb.toString();
	}
	
	public static String sanitizeString(String str) throws ServiceException {
		str = Normalizer.normalize(str, Form.NFKC);
		str = str.replace("^\\p{ASCII}]", "");
		Pattern pattern = Pattern.compile("<script>");
		Matcher matcher = pattern.matcher(str);
		if(matcher.find())
		{
			throw new ServiceException(new Throwable("Invalid input detected"));
		}
		return str;
	}
}
