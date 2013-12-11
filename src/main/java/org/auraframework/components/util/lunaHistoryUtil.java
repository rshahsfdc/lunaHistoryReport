package org.auraframework.components.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.InflaterInputStream;

import org.auraframework.components.ui.InputOption;
import org.auraframework.test.testsetrunner.lunaHistoryModel;
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
}
