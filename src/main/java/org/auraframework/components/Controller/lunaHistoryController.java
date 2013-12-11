package org.auraframework.components.Controller;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import org.auraframework.components.util.lunaHistoryUtil;
import org.auraframework.system.Annotations.AuraEnabled;
import org.auraframework.system.Annotations.Controller;
import org.auraframework.system.Annotations.Key;
import org.json.JSONException;

@Controller
public class lunaHistoryController {
	
	@AuraEnabled
    public static List<Object> echoSelect(@Key("url") String url, @Key("branchName") String branchName) throws IOException, JSONException, URISyntaxException {
		List<Object> failingTests = lunaHistoryUtil.getFailingTests(url + "/testReport/", branchName);
		return failingTests;
    }
}
