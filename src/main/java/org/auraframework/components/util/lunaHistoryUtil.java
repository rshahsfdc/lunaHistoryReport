package org.auraframework.components.util;

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

import org.apache.commons.codec.binary.Base64;
import org.auraframework.components.Model.lunaHistoryModel;
import org.auraframework.components.ui.InputOption;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class lunaHistoryUtil {
	public static final String UTF_ENCODING = "UTF-8";
	
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
	public static String readStringFromStream(InputStream is, boolean compressed, String encoding, int blobLengthHint) {
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

	public static List<InputOption> getBuildHistory(String jenkinsURL) throws IOException, JSONException, URISyntaxException {
		JSONObject json = lunaHistoryUtil.getJsonFromJenkins(jenkinsURL + lunaHistoryModel.API_JSON);
		JSONArray jsonArray = (JSONArray)json.get("builds");
		ArrayList<InputOption> filterOptions = new ArrayList<InputOption>();
		for (int dictIndex = 0; dictIndex < jsonArray.length(); dictIndex++) {
			JSONObject jsonDict = (JSONObject)jsonArray.get(dictIndex);
			String buildNumber = jsonDict.getString("number");
            String buildUrl = jsonDict.getString("url");
            filterOptions.add(new InputOption(buildNumber, buildNumber, false, buildUrl));
		}
		return filterOptions;
	}

	public static List<Object> getFailingTests(String reportUrl, String branchName) {
		JSONArray  jsonArray, jsonIntegrationArray = null, jsonOneArray = null;
		JSONObject  jsonDictIntegrationResults,jsonDictOneResults, jsonDict = null;
		String className = null, testName = null;
		List<Object> alltestFailures = null;
		try {
			JSONObject json = lunaHistoryUtil.getJsonFromJenkins(reportUrl + lunaHistoryModel.API_JSON);
			jsonArray = (JSONArray)json.get("suites");
			int currFailCount = json.getInt("failCount");
			if(currFailCount > 0){
				alltestFailures = new ArrayList<Object>();
				String branchId = lunaHistoryModel.BRANCHID.get(branchName.toLowerCase());
			
				// Aura Integration test failures
				jsonDictIntegrationResults = (JSONObject)jsonArray.get(0);
			    jsonIntegrationArray = (JSONArray)jsonDictIntegrationResults.get("cases");
			    
		        //One Sanity test failures
		        if(jsonArray.length() == 2){
		        	jsonDictOneResults = (JSONObject)jsonArray.get(1);
			        jsonOneArray = (JSONArray)jsonDictOneResults.get("cases");
			    }
		        
		        // Getting all test failures
		        jsonArray = concatArray(jsonIntegrationArray, jsonOneArray);
		         for (int dictIndex = 0; dictIndex < jsonArray.length(); dictIndex++) {
	                 jsonDict = (JSONObject)jsonArray.get(dictIndex);

	                 /*
	                  * Looking at only items That are actual failures. There are two results: FAILED REGRESSED
	                  */
	                if (jsonDict.get("status").equals("FAILED") || jsonDict.get("status").equals("REGRESSION")) {
	                	className = jsonDict.getString("className");
	                    testName = jsonDict.getString("name");
	                    String myurl = lunaHistoryModel.AUTOBUILD_URL + "?className=" + URLEncoder.encode(className, UTF_ENCODING) + "&testName=" + URLEncoder.encode(testName, "UTF-8") + "&branchId=" + branchId;
	                    Map<String, Object> testFailure = new HashMap<String, Object>();
	                	testFailure.put("className", className);
	                	testFailure.put("name", testName);
	                	testFailure.put("url", myurl);
	                	testFailure.put("isHidden", "");
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
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return alltestFailures;
		
	}
	
	private static JSONArray concatArray(JSONArray... arrs)
	        throws JSONException {
	    JSONArray result = new JSONArray();
	    for (JSONArray arr : arrs) {
	    	if(arr != null){
	    		for (int i = 0; i < arr.length(); i++) {
		            result.put(arr.get(i));
		        }
	    	}
	    }
	    return result;
	}
}
