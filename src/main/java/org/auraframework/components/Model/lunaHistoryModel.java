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
package org.auraframework.components.Model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.concurrent.ThreadSafe;

import org.auraframework.Aura;
import org.auraframework.components.ui.InputOption;
import org.auraframework.components.util.lunaHistoryUtil;
import org.auraframework.instance.BaseComponent;
import org.auraframework.system.AuraContext;
import org.auraframework.system.Annotations.AuraEnabled;
import org.auraframework.system.Annotations.Model;
import org.auraframework.throwable.quickfix.QuickFixException;
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
    public static final Map<String, String> BRANCHID = new HashMap<String, String>();
    static {
    	BRANCHID.put("main", "641");
    	BRANCHID.put("patch", "11282");
    	BRANCHID.put("freeze", "11382");
    }
    public static final String AUTOBUILD_URL = "https://build.soma.salesforce.com/build/Test/status";
	public static final String UTF_ENCODING = "UTF-8";
	private List<Object> alltestFailures;
	private int currFailCount;
	private String branchName;
	private Integer buildNumber;
	private String jenkinsURL;
	public lunaHistoryModel() throws QuickFixException {
		alltestFailures = new ArrayList<Object>();
		AuraContext context = Aura.getContextService().getCurrentContext();
        BaseComponent<?, ?> component = context.getCurrentComponent();

        branchName = (String) component.getAttributes().getValue("branchName");
        buildNumber = Integer.parseInt(component.getAttributes().getValue("buildNumber").toString());
		try{
			jenkinsURL = BRANCH.get(branchName.toLowerCase());
			BRANCHID.get(branchName.toLowerCase());
			filterOptions = lunaHistoryUtil.getBuildHistory(jenkinsURL);
			//Support to get test history from Previous build
			if(buildNumber > 0){
				jenkinsURL += buildNumber;
			}
			else{
				jenkinsURL += "lastCompletedBuild";
			}
			jenkinsURL += "/testReport/";
			alltestFailures = lunaHistoryUtil.getFailingTests(jenkinsURL, branchName);
			JSONObject json = lunaHistoryUtil.getJsonFromJenkins(jenkinsURL + API_JSON);
			currFailCount = json.getInt("failCount");
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
