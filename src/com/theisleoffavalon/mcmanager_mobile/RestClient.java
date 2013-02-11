/**
 * 
 */
package com.theisleoffavalon.mcmanager_mobile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import android.util.Log;

import com.theisleoffavalon.mcmanager_mobile.MinecraftCommand.ArgType;

/**
 * This class handles talking to the server <br />
 * Many methods in this class return a Map of <String,Object>, this map contains
 * various different values that were returned from a request as there can be
 * multiple returns with different return types. The mappings for these are the
 * ones default from json-simple
 * 
 * <pre>
 * 	JSON		Java
 * 	string		java.lang.String
 * 	number		java.lang.Number
 * 	true|false	java.lang.Boolean
 * 	null		null
 * 	array		java.util.List
 * 	object		java.util.Map
 * </pre>
 * 
 * These return types should be obvious from their names, but checked type
 * casting should be done.
 * 
 * @author Jacob Henkel
 */
public class RestClient {

	/**
	 * The root URL of the API
	 */
	private URL	rootUrl;

	/**
	 * Creates a rest client for the given parameters
	 * 
	 * @param protocol
	 *            The protocol to use, can be http or https
	 * @param host
	 *            The host to connect to
	 * @param port
	 *            The port to connect to
	 * @param apiRoot
	 *            The root of the API on the host
	 * @throws MalformedURLException
	 */
	public RestClient(String protocol, String host, int port, String apiRoot)
			throws MalformedURLException {

		this.rootUrl = new URL(protocol, host, port, apiRoot);
		Log.d("RestClient",
				String.format("Rest Client created for %s",
						this.rootUrl.toExternalForm()));
	}

	/**
	 * Sends a JSONRPC request
	 * 
	 * @param request
	 *            The request to send
	 * @return The response
	 */
	private JSONObject sendJSONRPC(JSONObject request) {
		JSONObject ret = null;

		try {
			HttpURLConnection conn = (HttpURLConnection) this.rootUrl
					.openConnection();
			conn.setRequestMethod("POST");
			conn.setChunkedStreamingMode(0);
			conn.setDoInput(true);
			conn.setDoOutput(true);
			OutputStreamWriter osw = new OutputStreamWriter(
					new BufferedOutputStream(conn.getOutputStream()));
			request.writeJSONString(osw);
			osw.close();

			if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
				InputStreamReader isr = new InputStreamReader(
						new BufferedInputStream(conn.getInputStream()));
				ret = (JSONObject) new JSONParser().parse(isr);
			} else {
				Log.e("RestClient", String.format(
						"Got %d instead of %d for HTTP Response",
						conn.getResponseCode(), HttpURLConnection.HTTP_OK));
				return null;
			}

		} catch (IOException e) {
			Log.e("RestClient", "Error in sendJSONRPC", e);
		} catch (ParseException e) {
			Log.e("RestClient", "Parse return data error", e);
		}
		return ret;
	}

	/**
	 * Creates a JSONRPC Object with required fields besides parameters set
	 * 
	 * @param method
	 *            The method called
	 * @return The object
	 */
	@SuppressWarnings("unchecked")
	private JSONObject createJSONRPCObject(String method) {
		UUID id = UUID.randomUUID();
		JSONObject request = new JSONObject();
		request.put("jsonrpc", JSONRpcValues.JSON_RPC_VERSION);
		request.put("method", method);
		request.put("id", id.toString());
		return request;
	}

	/**
	 * Checks the response for errors
	 * 
	 * @param response
	 *            The response
	 * @param request
	 *            The request sent for the response
	 * @throws IOException
	 *             If an error is encountered
	 */
	private void checkJSONResponse(JSONObject response, JSONObject request)
			throws IOException {
		if ((response == null) || (response.get("error") != null)) {
			Log.e("RestClient",
					String.format(
							"An error was encountered with the code %d with the message %s",
							response.get("code"), response.get("message")));
			throw new IOException("Invalid response from server");
		}
		if (!response.get("id").equals(request.get("id"))) {
			Log.e("RestClient", "Response ID doesn't match!");
			throw new IOException("Got the wrong id on response");
		}
	}

	/**
	 * Gets all available Minecraft commands on the server
	 * 
	 * @return A list of Minecraft commands
	 * @throws IOException
	 *             If an error is encountered
	 */
	public List<MinecraftCommand> getAllMinecraftCommands() throws IOException {
		List<MinecraftCommand> cmds = new ArrayList<MinecraftCommand>();
		JSONObject request = createJSONRPCObject("getAllCommands");
		JSONObject resp = sendJSONRPC(request);
		checkJSONResponse(resp, request);

		// Parse result
		JSONObject result = (JSONObject) resp.get("result");
		@SuppressWarnings("unchecked")
		Map<String, JSONObject> commands = (Map<String, JSONObject>) result
				.get("commands");
		for (String name : commands.keySet()) {
			JSONObject paramObj = commands.get(name);
			JSONArray jparams = (JSONArray) paramObj.get("params");
			JSONArray jparamTypes = (JSONArray) paramObj.get("paramTypes");

			Map<String, ArgType> params = new HashMap<String, ArgType>();
			for (int i = 0; i < jparams.size(); i++) {
				params.put((String) jparams.get(i), ArgType
						.getArgTypeFromString((String) jparamTypes.get(i)));
			}
			cmds.add(new MinecraftCommand(name, params));
		}
		return cmds;
	}

	/**
	 * Gets information about the server
	 * 
	 * @return A map containing information about the server
	 * @throws IOException
	 *             If a connection problem occurs
	 */
	public Map<String, Object> getServerInfo() throws IOException {
		Map<String, Object> ret = new HashMap<String, Object>();
		// Create request
		JSONObject request = createJSONRPCObject("systemInfo");
		// Send request
		JSONObject response = sendJSONRPC(request);
		checkJSONResponse(response, request);

		// Parse response
		@SuppressWarnings("unchecked")
		Map<String, Object> json = (JSONObject) response.get("result");
		ret.putAll(json);

		return ret;
	}

	/**
	 * Executes the given command on the server the RestClient is connected to
	 * 
	 * @param cmd
	 *            The command to execute
	 * @param params
	 *            The parameters to pass into the command
	 * @return A map containing any return values
	 * @throws IOException
	 *             If a connection problem occurs
	 */
	public Map<String, Object> executeCommand(MinecraftCommand cmd,
			Map<String, Object> params) throws IOException {
		Map<String, Object> ret = new HashMap<String, Object>();

		// Create request
		JSONObject request = cmd.createJSONObject(params);
		// Send request
		JSONObject response = sendJSONRPC(request);
		checkJSONResponse(response, request);

		// Parse response
		@SuppressWarnings("unchecked")
		Map<String, Object> json = (JSONObject) response.get("result");
		ret.putAll(json);

		return ret;
	}

	/**
	 * This method gets a list of all mods and their versions that are currently
	 * on the Minecraft server
	 * 
	 * @return A list of mods
	 * @throws IOException
	 *             If a connection problem occurs
	 */
	public List<MinecraftMod> getServerMods() throws IOException {
		List<MinecraftMod> mods = new ArrayList<MinecraftMod>();

		JSONObject request = createJSONRPCObject("getMods");
		JSONObject response = sendJSONRPC(request);
		checkJSONResponse(response, request);

		JSONObject result = (JSONObject) response.get("result");
		@SuppressWarnings("unchecked")
		List<JSONObject> modlist = (List<JSONObject>) result.get("mods");
		for (Map<String, String> m : modlist) {
			mods.add(new MinecraftMod(m.get("name"), m.get("version")));
		}

		return mods;

	}

	/**
	 * Stops the Minecraft server
	 * 
	 * @throws IOException
	 *             If a connection problem occurs
	 */
	public void stopServer() throws IOException {
		JSONObject request = createJSONRPCObject("stopServer");
		JSONObject response = sendJSONRPC(request);
		checkJSONResponse(response, request);
	}

	/**
	 * Gets a list of all methods on the server's JSON-RPC service
	 * 
	 * @return A list of methods
	 * @throws IOException
	 *             If a connection problem occurs
	 */
	@SuppressWarnings("unchecked")
	public List<String> getAllMethods() throws IOException {
		JSONObject request = createJSONRPCObject("getAllMethods");
		JSONObject response = sendJSONRPC(request);
		checkJSONResponse(response, request);

		// Parse response
		JSONObject json = (JSONObject) response.get("result");
		return (List<String>) json.get("methods");
	}
}
