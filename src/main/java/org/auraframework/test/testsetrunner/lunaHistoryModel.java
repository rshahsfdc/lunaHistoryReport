/*
 * Copyright (C) 2013 salesforce.com, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.auraframework.test.testsetrunner;

import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.concurrent.ThreadSafe;

import org.apache.commons.codec.binary.Base64;
import org.auraframework.Aura;
import org.auraframework.components.ui.InputOption;
import org.auraframework.components.util.lunaHistoryUtil;
import org.auraframework.instance.BaseComponent;
import org.auraframework.system.AuraContext;
import org.auraframework.system.Annotations.AuraEnabled;
import org.auraframework.system.Annotations.Model;
import org.auraframework.throwable.quickfix.QuickFixException;
import org.json.JSONArray;
import org.json.JSONObject;
 
/**
 * This model exposes a view on the {@link TestSetRunnerState} for
 * {@link AuraEnabled} access. Because all the model state is shared, this class
 * itself does not hold any state.
 */
@Model
@ThreadSafe
public class lunaHistoryModel {
	public static final String API_JSON = "api/json";
	private List<InputOption> filterOptions;
	private static final Map<String, String> BRANCH = new HashMap<String, String>();
    static {
    	BRANCH.put("main", "http://download.auraframework.org:8080/job/core-aura-integration/");
    	BRANCH.put("patch", "http://download.auraframework.org:8080/view/188-patch/job/02b-main-aura-integation-188-patch/");
    	BRANCH.put("freeze", "http://download.auraframework.org:8080/view/freeze/job/02b-main-aura-integation-freeze/");
    }
    private static final Map<String, String> BRANCHID = new HashMap<String, String>();
    static {
    	BRANCHID.put("main", "641");
    	BRANCHID.put("patch", "11282");
    	BRANCHID.put("freeze", "11382");
    }
    private static final String AUTOBUILD_URL = "https://build.soma.salesforce.com/build/Test/status";
	public static final String UTF_ENCODING = "UTF-8";
	private final List<Object> alltestFailures;
	private int currFailCount;
	private String branchName;
	private Integer buildNumber;
	private String jenkinsURL;
	private String branchId;
	
	public lunaHistoryModel() throws QuickFixException {
		alltestFailures = new ArrayList<Object>();
		AuraContext context = Aura.getContextService().getCurrentContext();
        BaseComponent<?, ?> component = context.getCurrentComponent();

        branchName = (String) component.getAttributes().getValue("branchName");
        buildNumber = Integer.parseInt(component.getAttributes().getValue("buildNumber").toString());
		try{
			JSONArray  jsonArray = null;
			JSONObject  jsonDict = null;
			String className = null, testName = null;
			jenkinsURL = BRANCH.get(branchName.toLowerCase());
			branchId = BRANCHID.get(branchName.toLowerCase());
			filterOptions = lunaHistoryUtil.getBuildHistory(jenkinsURL);
			//Support to get test history from Previous build
			if(buildNumber > 0){
				jenkinsURL += buildNumber;
			}
			else{
				jenkinsURL += "lastCompletedBuild";
			}
			jenkinsURL += "/testReport/";
			JSONObject json = lunaHistoryUtil.getJsonFromJenkins(jenkinsURL + API_JSON);
			 jsonArray = (JSONArray)json.get("suites");
			 currFailCount = json.getInt("failCount");
	         if(currFailCount > 0){
	        	 jsonDict = (JSONObject)jsonArray.get(1);
		         // Getting all test failures
		         jsonArray = (JSONArray)jsonDict.get("cases");
		             for (int dictIndex = 0; dictIndex < jsonArray.length(); dictIndex++) {
		                 jsonDict = (JSONObject)jsonArray.get(dictIndex);

		                 /*
		                  * Looking at only items That are actual failures. There are two results: FAILED REGRESSED
		                  */
		                if (jsonDict.get("status").equals("FAILED") || jsonDict.get("status").equals("REGRESSION")) {
		                	className = jsonDict.getString("className");
		                    testName = jsonDict.getString("name");
		                    String myurl = AUTOBUILD_URL + "?className=" + URLEncoder.encode(className, UTF_ENCODING) + "&testName=" + URLEncoder.encode(testName, "UTF-8") + "&branchId=" + branchId;
		                    Map<String, Object> testFailure = new HashMap<String, Object>();
		                	testFailure.put("className", className);
		                	testFailure.put("name", testName);
		                	testFailure.put("url", myurl);
		                	alltestFailures.add(testFailure);
		                	String login = "userName:pwd";
		                	  
		                	String encodedLogin = new String(Base64.encodeBase64(login.getBytes()));
		                	URL w2lUrl = new URL(myurl);
		                    URLConnection w2lConnection = w2lUrl.openConnection();
		                    w2lConnection.setRequestProperty("Authorization", "Basic " + encodedLogin);
		                    w2lConnection.setDoOutput(true);
		                    w2lConnection.setReadTimeout(60000);

		                    String resultHTML;
		                    try {
		                        resultHTML = lunaHistoryUtil.readStringFromStream(w2lConnection.getInputStream(), false, UTF_ENCODING, 65536);
		                    } catch (SocketTimeoutException e) {
		                        resultHTML = null;
		                    }
		                    String abstatus = "SUCCESSFUL";
		                    if (resultHTML == null) {
		                        abstatus = "Luna timeout";
		                    } else if (resultHTML.contains("Not Found!")) {
		                        abstatus = "NOT FOUND";
		                    } else if (resultHTML.contains("ERROR")) {
		                        abstatus = "ERROR";
		                    } else if (resultHTML.contains("FAILURE")) {
		                        if (resultHTML.contains("DETECTED FLAPPER FAILURE")) {
		                            abstatus = "FLAPPER";
		                        } else {
		                            abstatus = "FAILURE";
		                        }
		                    }
		                    testFailure.put("status", abstatus);
		                }
		             }// end of for loop
		        }
		    }
	         catch(Exception e){
		         e.printStackTrace();
		    }
	}
	
    @AuraEnabled
    public Object getTestsHistory() {
        return alltestFailures;
    }
    
    @AuraEnabled
    public Object getFilters() {
        return filterOptions;
    }
    
    @AuraEnabled
    public int getCurrentFailCount() {
        return currFailCount;
    }
    
    @AuraEnabled
    public String getJenkinsURL() {
        return jenkinsURL;
    }
}
