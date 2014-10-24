package org.beiwe.app.networking;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.client.methods.HttpPost;
import org.beiwe.app.DeviceInfo;
import org.beiwe.app.R;
import org.beiwe.app.session.LoginSessionManager;
import org.beiwe.app.storage.EncryptionEngine;
import org.beiwe.app.storage.TextFileManager;

import android.content.Context;
import android.util.Log;

//potentially useful:
// http://stackoverflow.com/questions/5643704/reusing-ssl-sessions-in-android-with-httpclient

/** 
 * @author Josh, Eli, Dor
 */

//FIXME: Eli.  determine if adding the parameters field to doFileUpload etc. broke things.  (I don't thiiiink it did...)

public class PostRequest {

	static String boundary = "gc0p4Jq0M2Yt08jU534c0p";
	static String newLine = "\n"; //we will use unix-style new lines
	static String attachmentName = "file";
	
	private static Context appContext;
	private static String patientID;
	private static String password;

	//FIXME: Eli. Solve the password hashing discrepancy between server and android
	private static String getUserPassword() { return EncryptionEngine.safeHash(password); }

	
	/**Uploads must be initialized with an appContext before they can access the wifi state or upload a _file_.
	 * @param some applicationContext */
	private PostRequest( Context applicationContext, LoginSessionManager session ) {
		appContext = applicationContext;
		patientID = session.getUserDetails().get( LoginSessionManager.KEY_ID );
		password = session.getUserDetails().get( LoginSessionManager.KEY_PASSWORD );
	}
	
	/** Simply runs the constructor, using the applcationContext to grab variables.  Idempotent. */
	public static void initializePostRequest(Context applicationContext, LoginSessionManager session) { new PostRequest(applicationContext, session); }
	
	
	/*##################################################################################
	 ############################ Public Wrappers ######################################
	 #################################################################################*/
	

	/**For use with Async tasks run from activities.
	 * This opens a connection with the server, sends the HTTP parameters, then receives a response code, and returns it.
	 * @param parameters
	 * @return serverResponseCode */
	public static int asyncRegisterHandler( String parameters, String url ) {
		try {
			return doRegisterRequest( parameters, new URL(url) ); }
		catch (MalformedURLException e) {
			Log.e("PostRequestFileUpload", "malformed URL");
			e.printStackTrace(); 
			return 0; }
		catch (IOException e) {
			e.printStackTrace();
			
			Log.e("PostRequest","Network error: " + e.getMessage());
			return 502; }
	}
	
	
	/**For use with Async tasks run from activities.
	 * Makes an HTTP post request with the provided URL and parameters, returns the server's response code from that request
	 * @param parameters
	 * @return an int of the server's response code from the HTTP request */
	public static int asyncPostHandler( String parameters, String url ) {
		try {
			return doPostRequestGetResponseCode( parameters, new URL(url) ); }
		catch (MalformedURLException e) {
			Log.e("PosteRequestFileUpload", "malformed URL");
			e.printStackTrace(); 
			return 0; }
		catch (IOException e) {
			Log.e("PostRequest","Unable to establish network connection");
			return 502; }
	}

	/**For use with Async tasks run from activities.
	 * Makes an HTTP post request with the provided URL and parameters, returns a string of the server's entire response. 
	 * @param urlString
	 * @return a string of the contents of the return from an HTML request.*/
	public static String asyncRequestString(String parameters, String urlString) throws NullPointerException {
		try { return doPostRequestGetResponseString(parameters, urlString); }
		catch (IOException e) {
			Log.e("PostRequest", "Download File failed with exception: " + e);
			Log.e("parameters", ": " + parameters);
			Log.e("url", ": " + urlString);
			e.printStackTrace();
			
			throw new NullPointerException(); }
	}
	
	/*##################################################################################
	 ################################ Common Code ######################################
	 #################################################################################*/
	
//	/** Convenience... */
//	private static HttpURLConnection setupHTTP( URL url ) throws IOException { return setupHTTP( "", url); }
	
	/**Creates an HTTP connection with common settings (reduces code clutter).
	 * @param url a URL object
	 * @return a new HttpURLConnection with common settings
	 * @throws IOException This function can throw 2 kinds of IO exceptions: IOExeptions ProtocolException*/
	private static HttpURLConnection setupHTTP( String parameters, URL url ) throws IOException {
		// Create a new HttpURLConnection and set its parameters
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setUseCaches(false);
		connection.setDoOutput(true);
		connection.setRequestMethod("POST");
		connection.setRequestProperty("Connection", "Keep-Alive");
		connection.setRequestProperty("Cache-Control", "no-cache");
		//TODO: Eli. research if the timeouts below are compatible with the file upload function.  There is no reason they *shouldn't*, but it is untested.
		connection.setConnectTimeout(3000);
		connection.setReadTimeout(5000);
		
		if ( parameters.length() > 0 ) {
			DataOutputStream request = new DataOutputStream(connection.getOutputStream());
			request.write( parameters.getBytes() );
			request.flush();
			request.close();
		}
		return connection;
	}
	
	/*##################################################################################
	 ############################ Private Workers ######################################
	 #################################################################################*/
		
	
	private static String doPostRequestGetResponseString(String parameters, String urlString) throws IOException {
		HttpURLConnection connection = setupHTTP( parameters, new URL( urlString ) );
		connection.connect();
		
		// read in data using a BufferedReader from the HTTP connection
		BufferedReader reader = new BufferedReader( new InputStreamReader(connection.getInputStream() ) );
		StringBuilder builder = new StringBuilder();
		String dumb = "";
		
		try { // Read into BufferedReader, append it to the StringBuilder, return string.
			while ((dumb = reader.readLine()) != null) { builder.append(dumb); }
		} catch (IOException e) {  //This is really for debugging, we want to be able to discern these buffering errors.
			throw new NullPointerException("there was an error in receiving file data.??"); }
		//FIXME: Eli/Josh.  This could be extremely unsafe behavior, research? fix?
		
		connection.disconnect();
		return builder.toString();
	}
	
	private static int doPostRequestGetResponseCode(String parameters, URL url) throws IOException {
		HttpURLConnection connection = setupHTTP(parameters, url);
		int response = connection.getResponseCode();
		connection.disconnect();
		return response;
	}
	
	
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	private static int doRegisterRequest(String parameters, URL url) throws IOException {
		HttpURLConnection connection = setupHTTP(parameters, url);
		Log.i("WRITE DOWN KEY", "" + TextFileManager.getKeyFile().read().length());
		
		// write to the keyfile if received 200 OK AND the file has nothing in it AND parameters has a bluetooth_id field
		if ( connection.getResponseCode() == 200
				&& TextFileManager.getKeyFile().read().length() == 0
				&& parameters.startsWith("bluetooth_id" ) ) {
			//FIXME: Eli. probably broke this string check due to the refactor, work out.
			// If the AsyncPostSender receives a 1, that means that someone misconfigured the server.
			//TODO: Eli. Why?
			try { createKey( readResponse(connection) ); }
			catch (NullPointerException e) { return 1; }
		}
		int response = connection.getResponseCode();
		connection.disconnect();
		return response;
	}

	
	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	//FIXME: we need to be doing connection.disconnect().  refactor to handle this gracefully,
	
	/** Constructs and sends a multipart HTTP POST request with a file attached
	 * Based on http://stackoverflow.com/a/11826317
	 * @param file the File to be uploaded
	 * @param uploadUrl the destination URL that receives the upload
	 * @return HTTP Response code as int
	 * @throws IOException */
	private static int doFileUpload(File file, String parameters, URL uploadUrl) throws IOException {		
		HttpURLConnection connection = setupHTTP( parameters, uploadUrl );
		connection.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
		DataOutputStream request = new DataOutputStream( connection.getOutputStream() );
		
		request.writeBytes("--" + boundary + newLine);
		request.writeBytes("Content-Disposition: form-data; name=\""
				+ attachmentName + "\";filename=\"" + file.getName() + "\"" + newLine + newLine);

		// Read in data from the file, and pour it into the POST request
		DataInputStream inputStream = new DataInputStream(new FileInputStream(file));
		int data;
		while( ( data = inputStream.read() ) != -1 ) request.write( (char) data );
		inputStream.close();

		// Add closing boundary etc. to mark the end of the POST request 
		request.writeBytes( newLine + "--" + boundary + "--" + newLine );
		request.flush();
		request.close();

		// Get HTTP Response
		Log.i("POSTREQUESTFILEUPLOAD", "RESPONSE = " + connection.getResponseMessage());
		int response = connection.getResponseCode();
		connection.disconnect();
		return response;
	}
	
	
	/*##################################################################################
	 ######################### Convenience Functions ###################################
	 #################################################################################*/

	
	/**Checks a string to see if it is an RSA key file, if so it writes it to the Key File. 
	 * @param response */
	private static void createKey(String response){
		if ( !response.toString().startsWith("MIIBI") ) {
			throw new NullPointerException ("Bad encryption key !!!" ); }
		//TODO: Eli.  This needs to alert the user or throw an exception that is caught and handled with a user error alert prompt.
		Log.i( "POSTREQUEST", "Received a key: " + response.toString() );
		TextFileManager.getKeyFile().write( response.toString() );
		//TODO: Eli.  handle case where a key already exists?
	}
	
	
	/**Reads in the response data from an HttpURLConnection, returns it as a String.
	 * @param connection an HttpURLConnection
	 * @return a String containing return data
	 * @throws IOException */
	private static String readResponse(HttpURLConnection connection) throws IOException {
		DataInputStream inputStream = new DataInputStream( connection.getInputStream() );
		BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream ) );
		String line;
		StringBuilder response = new StringBuilder();
		while ( (line = reader.readLine() ) != null) { response.append(line); }
		return response.toString();
	}
	
	
	//#######################################################################################
	//################################## File Upload ########################################
	//#######################################################################################

	
	/** Uploads all available files on a separate thread. */
	public static void uploadAllFiles() {
		// Run the HTTP POST on a separate thread
		// FIXME: Eli+Josh. Run through ALL code that uses network, we need to be running this check.
		if ( !NetworkUtility.getWifiState(appContext) ) { return; }
		ExecutorService executor = Executors.newFixedThreadPool(1);
		Callable<HttpPost> thread = new Callable<HttpPost>() {
			@Override
			public HttpPost call() {
				doTryUploadDelete( TextFileManager.getAllUploadableFiles() ) ;
				return null; //(indentation was stupid, made a function.)
			}
		};
		executor.submit(thread);
	}
	
	
	/** For each file name given, tries to upload that file.  If successful it then deletes the file.*/
	private static void doTryUploadDelete(String[] files) {
		for (String fileName : files) {
			try {
				if ( tryToUploadFile(fileName) ) { TextFileManager.delete(fileName); } }
			catch (IOException e) {
				Log.i( "Upload", "Failed to upload file " + fileName + ".\n Raised exception " + e.getCause() );
				e.printStackTrace();
			}
		}
		Log.i("NetworkUtilities", "Finished upload loop.");				
	}
	
	
	/**Try to upload a file to the server
	 * @param filename the short name (not the full path) of the file to upload
	 * @return TRUE if the server reported "200 OK"; FALSE otherwise */
	private static Boolean tryToUploadFile(String filename) throws IOException{
		URL uploadUrl = new URL( appContext.getResources().getString(R.string.data_upload_url) );
		File file = new File( appContext.getFilesDir() + "/" + filename );
		
		// request was successful (returned "200 OK"), return TRUE
		if ( PostRequest.doFileUpload( file, "", uploadUrl ) == 200 ) { return true; }
		return false;
		// request failed (returned something other than 200), return FALSE
	}
	
	
	//#######################################################################################
	//############################### UTILITY FUNCTIONS #####################################
	//#######################################################################################

	/**This is a method used as an intermediate in order to shorten the length of logic trees.
	 * Method checks a given response code sent from the server, and then returns a string corresponding to the code,
	 * in order to display that to the user.
	 * @param responseCode
	 * @return String to be displayed on the Alert in case of a problem	 */
	public static String handleServerResponseCodes(int responseCode) {
		if (responseCode == 200) {return "OK";}
		else if (responseCode == 403) { return "Patient ID did not match Password on the server";}
		else if (responseCode == 405) { return "Phone is not registered to this user. Please contact research staff";}
		else if (responseCode == 502) { return "Please connect to the internet and try again";}
		//TODO: Eli. investigate what response code = 1 means in java? python?
		else if (responseCode == 1) { return "Someone misconfigured the server, please contact staff";}
		else { return "Internal server error..."; }
	}
	
	public static String makeParameter(String key, String value) { return key + "=" + value + "&"; }
	
	public static String defaultParameters() { 
		return makeParameter("patient_id", patientID ) +
				makeParameter("password", getUserPassword() ) +
				makeParameter("device_id", DeviceInfo.getAndroidID() );
	}
}