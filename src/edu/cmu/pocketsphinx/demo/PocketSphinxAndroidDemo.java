package edu.cmu.pocketsphinx.demo;

import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class PocketSphinxAndroidDemo extends Activity implements OnTouchListener, RecognitionListener, OnInitListener {

	// new
	//variable for checking Voice Recognition support on user device
	private static final int VR_REQUEST = 999;

	//variable for checking TTS engine data on user device
	private int MY_DATA_CHECK_CODE = 0;

	//Text To Speech instance
	private TextToSpeech myTTS; 

	//ListView for displaying suggested words
	//	private ListView wordList;

	//Log tag for output information
	//	private final String LOG_TAG = "SpeechRepeatActivity";
	// new

	static {
		System.loadLibrary("pocketsphinx_jni");
	}
	/**
	 * Recognizer task, which runs in a worker thread.
	 */
	RecognizerTask rec;
	/**
	 * Thread in which the recognizer task runs.
	 */
	Thread rec_thread;
	/**
	 * Time at which current recognition started.
	 */
	Date start_date;
	/**
	 * Number of seconds of speech.
	 */
	float speech_dur;
	/**
	 * Are we listening?
	 */
	boolean listening;
	/**
	 * Progress dialog for final recognition.
	 */
	ProgressDialog rec_dialog;
	/**
	 * Performance counter view.
	 */
	TextView performance_text;
	/**
	 * Editable text view.
	 */
	EditText edit_text;

	/*
	 * TODO
	 * This is not the right way to do this.
	 * Need to restart listening so that the hyp strings do not become too long.
	 * 
	 */
	int hitCounter = 0;
	String lastHyp;
	boolean turnOff = false;

	/**
	 * Respond to touch events on the Speak button.
	 * 
	 * This allows the Speak button to function as a "push and hold" button, by
	 * triggering the start of recognition when it is first pushed, and the end
	 * of recognition when it is released.
	 * 
	 * @param v
	 *            View on which this event is called
	 * @param event
	 *            Event that was triggered.
	 */
	public boolean onTouch(View v, MotionEvent event) {
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			start_date = new Date();
			this.listening = true;
			this.rec.start();
			break;
		case MotionEvent.ACTION_UP:
			Date end_date = new Date();
			long nmsec = end_date.getTime() - start_date.getTime();
			this.speech_dur = (float)nmsec / 1000;
			if (this.listening) {
				Log.d(getClass().getName(), "Showing Dialog");
				this.rec_dialog = ProgressDialog.show(PocketSphinxAndroidDemo.this, "", "Recognizing speech...", true);
				this.rec_dialog.setCancelable(false);
				this.listening = false;
			}
			this.rec.stop();
			break;
		default:
			;
		}
		/* Let the button handle its own state */
		return false;
	}

	/** Called when the activity is first created. */
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		this.rec = new RecognizerTask();
		this.rec_thread = new Thread(this.rec);
		//		this.listening = false;


		// new
		//		wordList = (ListView) findViewById(R.id.word_list);
		myTTS = new TextToSpeech(this, this);
		//find out whether speech recognition is supported
		PackageManager packManager = getPackageManager();
		List<ResolveInfo> intActivities = packManager.queryIntentActivities
				(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
		if (intActivities.size() != 0) {
			//prepare the TTS to repeat chosen words
			Intent checkTTSIntent = new Intent();  
			//check TTS data  
			checkTTSIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);  
			//start the checking Intent - will retrieve result in onActivityResult
			startActivityForResult(checkTTSIntent, MY_DATA_CHECK_CODE); 
		}
		else {
			//speech recognition not supported, disable button and output message
			Toast.makeText(this, "Oops - Speech recognition not supported!", Toast.LENGTH_LONG).show();
		}

		//new

		//		Button b = (Button) findViewById(R.id.Button01);
		//		b.setOnTouchListener(this);
		//		this.performance_text = (TextView) findViewById(R.id.PerformanceText);
		this.edit_text = (EditText) findViewById(R.id.EditText01);
		this.rec.setRecognitionListener(this);
		this.rec_thread.start();

		//		start_date = new Date();
		this.listening = true;
		this.rec.start();
	}

	/** Called when partial results are generated. */
	public void onPartialResults(Bundle b) {
		final PocketSphinxAndroidDemo that = this;
		final String hyp = b.getString("hyp");
		that.edit_text.postDelayed(new Runnable() {
			public void run() {
				if (hyp != null){
					if (hyp.endsWith("hello")) {
						if (!hyp.equals(lastHyp)) {
							Toast.makeText(getApplicationContext(), String.format("%s\nPrevious:%s\nCurrent:%s", hitCounter, lastHyp, hyp), Toast.LENGTH_SHORT).show();
							lastHyp = hyp;
							hitCounter++;
							turnOff = true;
							listening = false;
							rec.stop();
							listenToSpeech();
						}
					}
					//				that.edit_text.setText(hyp);
				}
			}
		}, 800);
	}

	/** Called with full results are generated. */
	public void onResults(Bundle b) {
		//			final String hyp = b.getString("hyp");
		//			final PocketSphinxAndroidDemo that = this;
		//			this.edit_text.post(new Runnable() {
		//				public void run() {
		//					that.edit_text.setText(hyp);
		//					Date end_date = new Date();
		//					long nmsec = end_date.getTime() - that.start_date.getTime();
		//					float rec_dur = (float)nmsec / 1000;
		//					that.performance_text.setText(String.format("%.2f seconds %.2f xRT",
		//							that.speech_dur,
		//							rec_dur / that.speech_dur));
		//					Log.d(getClass().getName(), "Hiding Dialog");
		//					that.rec_dialog.dismiss();
		//				}
		//			});
	}

	public void onError(int err) {
		final PocketSphinxAndroidDemo that = this;
		that.edit_text.post(new Runnable() {
			public void run() {
				that.rec_dialog.dismiss();
			}
		});
	}


	/**
	 * Instruct the app to listen for user speech input
	 */
	private void listenToSpeech() {
		//start the speech recognition intent passing required data
		Intent listenIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
		//indicate package
		listenIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getClass().getPackage().getName());
		//message to display while listening
		listenIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a word!");
		//set speech model
		listenIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
		//specify number of results to retrieve
		listenIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10);
		//start listening
		startActivityForResult(listenIntent, VR_REQUEST);
	}


	/**
	 * onActivityResults handles:
	 *  - retrieving results of speech recognition listening
	 *  - retrieving result of TTS data check
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		//check speech recognition result 
		if (requestCode == VR_REQUEST && resultCode == RESULT_OK) {
			Log.d("gmh", "result ok");
			//store the returned word list as an ArrayList
			ArrayList<String> transcriptions = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
			String queryString;
			try {
				Log.d("gmh", "trying API");
				queryString = URLEncoder.encode(transcriptions.get(0), "UTF-8");
				Log.d("gmh", "query: " + queryString);
				String url = String.format("http://ec2-50-17-103-0.compute-1.amazonaws.com:8000/cerie?h=%s", queryString);
				Log.d("gmh", "url: " + url);
				RequestTask requestTask = new RequestTask();
				Log.d("gmh", "executing");
				requestTask.execute(url);
			} catch (UnsupportedEncodingException e) {
				Log.d("gmh", "API request failed");
				Toast.makeText(this, "Sorry! Your query could not be processed.", Toast.LENGTH_LONG).show();
				e.printStackTrace();
			}

			//set the retrieved list to display in the ListView using an ArrayAdapter
			//			wordList.setAdapter(new ArrayAdapter<String> (this, R.layout.word, transcriptions));
		}

		//returned from TTS data check
		if (requestCode == MY_DATA_CHECK_CODE) { 
			Log.d("gmh", "check TTS code");
			//we have the data - create a TTS instance
			if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS)  
				myTTS = new TextToSpeech(this, this);
			//data not installed, prompt the user to install it  
			else {  
				//intent will take user to TTS download page in Google Play
				Intent installTTSIntent = new Intent();  
				installTTSIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);  
				startActivity(installTTSIntent);  
			}  
		}

		Log.d("gmh", "trying to restart");
		listening = true;
		rec.start();
		//call superclass method
		super.onActivityResult(requestCode, resultCode, data);
	}


	/**
	 * onInit fires when TTS initializes
	 */
	public void onInit(int initStatus) { 
		//if successful, set locale
		if (initStatus == TextToSpeech.SUCCESS)   
			myTTS.setLanguage(Locale.US);
	}


	/**
	 * @param value	the results of the async task
	 */
	public void onAsyncRequestResult(String value){
		Log.d("gmh", "doing TTS now");
		//		Toast.makeText(this, "Got value from async: " + value, Toast.LENGTH_LONG).show();
		myTTS.speak(value, TextToSpeech.QUEUE_FLUSH, null);
	}


	/**
	 * @author gavin
	 *
	 * An asynchronous task that executes in a thread separate from the UI thread.
	 * Calls onAsyncRequestResult with the result.
	 * 
	 * url	the URL for the GET request
	 */
	public class RequestTask extends AsyncTask<String, String, String>{

		@Override
		protected String doInBackground(String... uri) {
			Log.d("gmh", "un requestTask");
			HttpClient httpclient = new DefaultHttpClient();
			HttpResponse response;
			String responseString = null;
			try {
				Log.d("gmh", "trying request for real");
				response = httpclient.execute(new HttpGet(uri[0]));
				StatusLine statusLine = response.getStatusLine();
				if(statusLine.getStatusCode() == HttpStatus.SC_OK){
					ByteArrayOutputStream out = new ByteArrayOutputStream();
					response.getEntity().writeTo(out);
					out.close();
					responseString = out.toString();
				} else{
					// Closes the connection.
					response.getEntity().getContent().close();
					throw new IOException(statusLine.getReasonPhrase());
				}
			} catch (ClientProtocolException e) {
				//TODO Handle problems..
			} catch (IOException e) {
				//TODO Handle problems..
			}
			return responseString;
		}

		@Override
		protected void onPostExecute(String result) {
			onAsyncRequestResult(result);
		}

	}


}