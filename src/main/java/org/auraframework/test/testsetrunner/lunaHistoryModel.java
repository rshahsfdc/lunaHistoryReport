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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.InflaterInputStream;

import javax.annotation.concurrent.ThreadSafe;

import org.apache.commons.codec.binary.Base64;
import org.auraframework.Aura;
import org.auraframework.instance.BaseComponent;
import org.auraframework.system.AuraContext;
import org.auraframework.system.Annotations.AuraEnabled;
import org.auraframework.system.Annotations.Model;
import org.auraframework.throwable.quickfix.QuickFixException;
import org.json.JSONArray;
import org.json.JSONException;
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
	private static final Map<String, String> BRANCH = new HashMap<String, String>();
    static {
    	BRANCH.put("main", "http://download.auraframework.org:8080/job/core-aura-integration/lastCompletedBuild/testReport/");
    	BRANCH.put("patch", "http://download.auraframework.org:8080/view/188-patch/job/02b-main-aura-integation-188-patch/lastCompletedBuild/testReport/");
    	BRANCH.put("freeze", "http://download.auraframework.org:8080/view/freeze/job/02b-main-aura-integation-freeze/lastCompletedBuild/testReport/");
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
	private String jenkinsURL;
	private String branchId;
	
	public lunaHistoryModel() throws QuickFixException {
		alltestFailures = new ArrayList<Object>();
		AuraContext context = Aura.getContextService().getCurrentContext();
        BaseComponent<?, ?> component = context.getCurrentComponent();

        branchName = (String) component.getAttributes().getValue("branchName");
		try{
			JSONArray  jsonArray = null;
			JSONObject  jsonDict = null;
			String className = null, testName = null;
			jenkinsURL = BRANCH.get(branchName.toLowerCase());
			branchId = BRANCHID.get(branchName.toLowerCase());
			JSONObject json = getJsonFromJenkins(jenkinsURL + API_JSON);
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
		                        resultHTML = readStringFromStream(w2lConnection.getInputStream(), false, UTF_ENCODING, 65536);
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
    public int getCurrentFailCount() {
        return currFailCount;
    }
    
    @AuraEnabled
    public String getJenkinsURL() {
        return jenkinsURL;
    }
    
	public static JSONObject getJsonFromJenkins(String uri) throws IOException, JSONException, URISyntaxException {
        BufferedReader reader = null;
        try {
            URL url = new URL(uri);
            reader = new BufferedReader(new InputStreamReader(url.openStream()));
            return new JSONObject(reader.readLine());
        } finally {
            if (reader != null) reader.close();
        }
    }
	
	@SuppressWarnings("resource")
	public String readStringFromStream(InputStream is, boolean compressed, String encoding, int blobLengthHint) {
        StringBuilder sb = new StringBuilder(Math.min(blobLengthHint, 32));
        try {
            if (compressed) {
                is = new InflaterInputStream(is);
            }
            byte[] buffer = new byte[8192]; // if the buffer size changes, update TextUtilTest.testReadStringFromBlob
            int bytesRead;
            while ((bytesRead = is.read(buffer)) > -1) {
                sb.append(new String(buffer, 0, bytesRead, encoding));
            }
        }
        catch (UnsupportedEncodingException e) {
            // shouldn't ever happen
            throw new RuntimeException(e);
        }
        catch (IOException e) {
            return null;
        }
        finally {
            if (is != null)
                try {
                    is.close();
                }
                catch (IOException x) { /* do nothing */}
        }
        return sb.toString();
    }
}
