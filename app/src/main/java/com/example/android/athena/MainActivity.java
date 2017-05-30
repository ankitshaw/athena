package com.example.android.athena;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcel;
import android.provider.AlarmClock;
import android.provider.ContactsContract;
import android.support.customtabs.CustomTabsIntent;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.method.TransformationMethod;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Scanner;

import javax.net.ssl.HttpsURLConnection;

import ai.api.AIConfiguration;
import ai.api.AIDataService;
import ai.api.AIListener;
import ai.api.AIService;
import ai.api.AIServiceException;
import ai.api.model.AIError;
import ai.api.model.AIRequest;
import ai.api.model.AIResponse;
import ai.api.model.Result;

import static com.example.android.athena.R.drawable.textview;
import static java.lang.Boolean.TRUE;

public class MainActivity extends AppCompatActivity implements AIListener {
    private static final String TAG = "ChatActivity";
    private ChatArrayAdapter chatArrayAdapter;
    private ListView listView;
    private EditText chatText;
    private FloatingActionButton sendButton;
    private FloatingActionButton listenButton;
    private AIService aiService;
    private Animation pop_in_anim;
    private Animation pop_out_anim;

    private boolean rightSide = true; //true if you want message on right rightSide

    //addition
    private AIDataService aiDataService;
    Result result;
    private TextView rTextView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TTS.init(getApplicationContext());

        sendButton = (FloatingActionButton) findViewById(R.id.btn_send);
        listView = (ListView) findViewById(R.id.msgview);
        listenButton = (FloatingActionButton) findViewById(R.id.btn_mic);
        chatText = (EditText) findViewById(R.id.msg);

        pop_in_anim = AnimationUtils.loadAnimation(this, R.anim.pop_in);
        pop_out_anim = AnimationUtils.loadAnimation(this, R.anim.pop_out);
        sendButton.setAnimation(pop_out_anim);
        sendButton.setAnimation(pop_in_anim);
        listenButton.setAnimation(pop_in_anim);
        listenButton.setAnimation(pop_out_anim);
        sendButton.clearAnimation();
        listenButton.clearAnimation();

        chatArrayAdapter = new ChatArrayAdapter(this, R.layout.right);
        listView.setAdapter(chatArrayAdapter);

        rTextView = (TextView) findViewById(R.id.msgr);
        rTextView.setTransformationMethod(new LinkTransformationMethod());
        rTextView.setMovementMethod(LinkMovementMethod.getInstance());


        chatText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }
            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 0 && listenButton.getVisibility() == View.GONE) {
                    sendButton.clearAnimation();
                    sendButton.startAnimation(pop_out_anim);
                    sendButton.setVisibility(View.GONE);
                    sendButton.setEnabled(false);
                    listenButton.clearAnimation();
                    listenButton.setVisibility(View.VISIBLE);
                    listenButton.startAnimation(pop_in_anim);
                    listenButton.setEnabled(true);

                } else if (s.length() > 0 && sendButton.getVisibility() == View.GONE) {
                    listenButton.clearAnimation();
                    listenButton.startAnimation(pop_out_anim);
                    listenButton.setVisibility(View.GONE);
                    listenButton.setEnabled(false);
                    sendButton.clearAnimation();
                    sendButton.setVisibility(View.VISIBLE);
                    sendButton.startAnimation(pop_in_anim);
                    sendButton.setEnabled(true);
                }
            }
          //  MyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        });

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                String query= "";
                query = chatText.getText().toString();
                //sendChatMessage(query);
                sendRequest(query);

              // sendResponse(result.getFulfillment().getSpeech());
            }
        });

        listenButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                aiService.startListening();
            }
        });

        listView.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        listView.setAdapter(chatArrayAdapter);

        //to scroll the list view to bottom on data change
        chatArrayAdapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                listView.setSelection(chatArrayAdapter.getCount() - 1);
            }
        });

        final AIConfiguration config = new AIConfiguration("apiaikey",
                AIConfiguration.SupportedLanguages.English,
                AIConfiguration.RecognitionEngine.System);

        aiService = AIService.getService(this, config);
        //addition
        aiDataService = new AIDataService(this, config);

        aiService.setListener(this);

        sendResponse("Hello!! Welcome Back.");
        TTS.speak("Hello!! Welcome Back.");
        sendResponse("How may I help you?");
        TTS.speak("How may I help you?");
    }

    private boolean sendResponse(String text) {
        if (text.length() == 0)
            return false;
        chatArrayAdapter.add(new ChatMessage(!rightSide, text));
        return true;
    }

    private boolean sendChatMessage(String text) {
        if (text.length() == 0)
            return false;
        chatArrayAdapter.add(new ChatMessage(rightSide, text));
        chatText.setText("");
        return true;
    }

    public void onResult(final AIResponse response) { // here process response
        result = response.getResult();

        sendChatMessage(response.getResult().getResolvedQuery());

        if (result.getAction().equals("weather")) {

            final String weather_baseUrl = "http://api.openweathermap.org/data/2.5/weather";
            final String param_city = "q";
            final String param_unit = "units";
            final String param_appId = "APPID";
            final String weather_city = result.getStringParameter("city");

            Uri builtUri = Uri.parse(weather_baseUrl).buildUpon()
                    .appendQueryParameter(param_city, weather_city)
                    .appendQueryParameter(param_unit, "metric")
                    .appendQueryParameter(param_appId, "key")
                    .build();

            URL url = null;
            try {
                url = new URL(builtUri.toString());
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            String weather_result = null;

            new WeatherTest().execute(url);
        }
        else if (result.getAction().equals("meaning")) {

            final String meaning_word = result.getStringParameter("word").toLowerCase();
            final String meaning_baseUrl = "https://od-api.oxforddictionaries.com/api/v1/entries/en/" + meaning_word + "/definitions";
            Uri builtUri = Uri.parse(meaning_baseUrl);

            URL url = null;
            try {
                url = new URL(builtUri.toString());
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            String meaning_result = null;
            final String speech = result.getFulfillment().getSpeech();

            new MeaningTest().execute(url);

        }
        else if(result.getAction().equals("alarm")){

            String []time;
            time = result.getStringParameter("time").split(":");
            createAlarm("Athena",Integer.parseInt(time[0]),Integer.parseInt(time[1]));
            sendResponse("Alarm Set for: " +result.getStringParameter("time"));
            TTS.speak("Alarm Set");
        }
        else if(result.getAction().equals("timer")){

            String msg;
            msg = result.getStringParameter("any");
            int duration = Integer.parseInt(result.getStringParameter("any1"));
            if(msg == "")
                msg = "Athena Timer";
            startTimer(msg,duration*60);
            sendResponse("Timer set for " + result.getStringParameter("duration") +" minutes");
            TTS.speak("Timer Started.");
        }
        else if(result.getAction().equals("search")){

            String site = result.getStringParameter("any1");
            String Url ;
            if(site.toLowerCase().equals("wikipedia"))
                Url  = "https://en.wikipedia.org/wiki/"+result.getStringParameter("any");
            else
                Url  = "https://www.google.co.in/#q="+result.getStringParameter("any")+"&*";
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            builder.setStartAnimations(this, R.anim.slide_in_right, R.anim.slide_out_left);
            builder.setExitAnimations(this, R.anim.slide_in_left, R.anim.slide_out_right);
            CustomTabsIntent customTabsIntent = builder.build();
            customTabsIntent.launchUrl(this, Uri.parse(Url));
            sendResponse("Searching for " +result.getStringParameter("any"));
        }
        else if(result.getAction().equals("call")){

            String name = "";
            name = result.getStringParameter("given-name").toLowerCase()+" "+result.getStringParameter("last-name").toLowerCase();
            if(name.charAt(name.length()-1)==' ')
                name = name.substring(0,name.length()-1);
            final String s = getPhoneNumber(name,MainActivity.this);
            Intent callIntent = new Intent(Intent.ACTION_DIAL); callIntent.setData(Uri.parse("tel:"+s));
            startActivity(callIntent);
            sendResponse("Dailing "+ name);
            TTS.speak("Dailing "+name);
        }
        else if(result.getAction().equals("locationDistance"))
        {
            final String distance_baseurl = "https://maps.googleapis.com/maps/api/distancematrix/json?";
            final String distance_units = "units";
            final String distance_origin = "origins";
            final  String distance_destination = "destinations";
            final String distance_key = "key";
            final String city_origin = result.getStringParameter("geo-city");
            final String city_destination = result.getStringParameter("geo-city1");

            Uri builtUri = Uri.parse(distance_baseurl).buildUpon().appendQueryParameter(distance_units,"metric")
                    .appendQueryParameter(distance_origin,city_origin).appendQueryParameter(distance_destination,city_destination)
                    .appendQueryParameter(distance_key,"key").build();
            URL url = null;

            try {
                url = new URL(builtUri.toString());
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            String location_result=null;
            new LocationTest().execute(url);
        }


        else if(result.getAction().equals("news"))
        {
            Uri builtUri;
            final String news_baseurl = "https://newsapi.org/v1/articles?source=the-hindu&sortBy=latest";
            final String tech_baseurl = "https://newsapi.org/v1/articles?source=techcrunch&sortBy=latest";
            final String cric_baseurl = "https://newsapi.org/v1/articles?source=espn-cric-info&sortBy=latest";
            final String sport_baseurl = "https://newsapi.org/v1/articles?source=espn";
            final String biz_baseurl = "https://newsapi.org/v1/articles?source=the-economist&sortBy=latest";
            final String news_key = "apikey";
            final String category = "category";
            if(result.getStringParameter("any").toLowerCase().equals("technology"))
            {

                builtUri = Uri.parse(tech_baseurl).buildUpon()
                        .appendQueryParameter(news_key,"key").build();
                URL url = null;
            }
            else if(result.getStringParameter("any").toLowerCase().equals("business"))
            {

                builtUri = Uri.parse(biz_baseurl).buildUpon()
                        .appendQueryParameter(news_key,"key").build();
                URL url = null;
            }
            else if(result.getStringParameter("any").toLowerCase().equals("cricket"))
            {

                builtUri = Uri.parse(cric_baseurl).buildUpon()
                        .appendQueryParameter(news_key,"key").build();
                URL url = null;
            }
            else if(result.getStringParameter("any").toLowerCase().equals("sports"))
            {

                builtUri = Uri.parse(sport_baseurl).buildUpon()
                        .appendQueryParameter(news_key,"key").build();
                URL url = null;
            }
            else
            {
                builtUri = Uri.parse(news_baseurl).buildUpon()
                        .appendQueryParameter(news_key,"key").build();
                URL url = null;
            }


            URL url = null;

            try {
                url = new URL(builtUri.toString());
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            String news_result=null;
            new NewsTest().execute(url);
        }


        else if(result.getAction().equals("numberFact"))
        {
            final String number_baseurl = "http://numbersapi.com/";

            final String number_key = "key";
            final String number = result.getStringParameter("number");
            final String Url_fin = number_baseurl+number;

            Uri builtUri = Uri.parse(Url_fin);
            URL url = null;

            try {
                url = new URL(builtUri.toString());
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            String number_result=null;
            new NumberTest().execute(url);
        }
        else
        {
            sendResponse(result.getFulfillment().getSpeech());
            TTS.speak(result.getFulfillment().getSpeech());
        }


    }

    private void sendRequest(String s) {

        final String queryString = s;
        Log.d(TAG, queryString);

        final AsyncTask<String, Void, AIResponse> task = new AsyncTask<String, Void, AIResponse>() {

            private AIError aiError;

            @Override
            protected AIResponse doInBackground(final String... params) {
                final AIRequest request = new AIRequest();
                String query = params[0];


                //String event = params[1];

                if (!TextUtils.isEmpty(query))
                    request.setQuery(query);

                try {
                    return aiDataService.request(request);
                } catch (final AIServiceException e) {
                    aiError = new AIError(e);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(final AIResponse response) {
                if (response != null) {
                    onResult(response);
                } else {
                    onError(aiError);
                }
            }
        };
        task.execute(queryString);
    }

    //Http Request Response
    public static String getResponseFromHttpUrl(URL url) throws IOException {
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        try {
            InputStream in = urlConnection.getInputStream();

            Scanner scanner = new Scanner(in);
            scanner.useDelimiter("\\A");

            boolean hasInput = scanner.hasNext();
            if (hasInput) {
                return scanner.next();
            } else {
                return null;
            }
        } finally {
            urlConnection.disconnect();
        }
    }

    //Async Task Weather Serivce
    public class WeatherTest extends AsyncTask<URL, Void, String> {
        @Override
        protected String doInBackground(URL... urls) {
            URL searchUrl = urls[0];
            String r="";
            String s="";
            String weather_result = null;
            try {
                weather_result = getResponseFromHttpUrl(searchUrl);
                try {
                    JSONObject weather_info = new JSONObject(weather_result);
                    JSONObject main_info = weather_info.getJSONObject("main");
                    JSONArray short_info = weather_info.getJSONArray("weather");
                    JSONObject short_info_desc = short_info.getJSONObject(0);
                    String desc = short_info_desc.getString("description");
                    String city = weather_info.getString("name");
                    String temp = main_info.getString("temp");
                    String humidity = main_info.getString("humidity");
                    r = desc + " in " + city + "\nTemp: " + temp + "°" + "\nHumidity: "+ humidity + "%";
                    s = desc + " in " + city + ", Temperature: " + temp + "degree celsius" + ", Humidity: " + humidity + "percent"  ;
                    TTS.speak(s);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
            return r;
        }

        @Override
        protected void onPostExecute(String s) {
            if (s != null && !s.equals("")) {
                sendResponse(s);
            }
        }
    }

    //Meaning
    public class MeaningTest extends AsyncTask<URL, Void, String[]> {
        int len ;
        @Override
        protected String[] doInBackground(URL... urls) {
            final String app_id = "id";
            final String app_key = "key";
            URL searchUrl = urls[0];
            String[] r = new String[20];
            String meaning_result = null;
            try {
                HttpsURLConnection urlConnection = (HttpsURLConnection) searchUrl.openConnection();
                urlConnection.setRequestProperty("Accept", "application/json");
                urlConnection.setRequestProperty("app_id", app_id);
                urlConnection.setRequestProperty("app_key", app_key);

                try {
                    InputStream in = urlConnection.getInputStream();

                    Scanner scanner = new Scanner(in);
                    scanner.useDelimiter("\\A");

                    boolean hasInput = scanner.hasNext();
                    if (hasInput) {
                        meaning_result = scanner.next();
                    } else {
                        return null;
                    }
                } finally {
                    urlConnection.disconnect();
                }
                try {

                    String speech = "";
                    JSONObject meaning_info = new JSONObject(meaning_result);

                    JSONArray lexicalEntries = meaning_info.getJSONArray("results").getJSONObject(0).getJSONArray("lexicalEntries");
                    len = lexicalEntries.length();
                    for (int i = 0; i < lexicalEntries.length(); i++) {

                        JSONArray entries = lexicalEntries.getJSONObject(i).getJSONArray("entries");
                        String lexical_category = lexicalEntries.getJSONObject(i).getString("lexicalCategory");
                        r[i] = lexical_category + ":\n";
                        speech = speech + "As a " + lexical_category + " it means";
                        for (int k = 0; k < entries.length(); k++) {
                            JSONArray senses = entries.getJSONObject(k).getJSONArray("senses");
                            for (int j = 0; j < senses.length(); j++) {
                                String definition = senses.getJSONObject(j).getJSONArray("definitions").getString(0);

                                r[i] = r[i] + "~ " + definition + "\n";
                                if (senses.length() > 1 && j != senses.length() - 1)
                                    speech = speech + " " + definition + " or ";
                                else
                                    speech = speech + " " + definition;

                            }
                        }

                    }
                    TTS.speak(speech);

                } catch (JSONException e) {
                    e.printStackTrace();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
            return r;
        }

        @Override
        protected void onPostExecute(String[] s) {
            if (s != null && !s.equals("")) {
                //sendResponse("Thanks For Asking");
                for(int i=0;i<len;i++)
                    sendResponse(s[i]);
            }
        }
    }

    public class LocationTest extends AsyncTask<URL, Void, String> {
        @Override
        protected String doInBackground(URL... urls) {
            URL searchUrl = urls[0];
            String r="";
            String location_result = null;
            try {
                location_result = getResponseFromHttpUrl(searchUrl);
                try {
                    JSONObject distance_info = new JSONObject(location_result);
                    JSONArray info = distance_info.getJSONArray("rows");
                    JSONObject rows = info.getJSONObject(0);
                    JSONArray elements = rows.getJSONArray("elements");
                    JSONObject ele_info = elements.getJSONObject(0);
                    JSONObject distance = ele_info.getJSONObject("distance");
                    String location_distance = distance.getString("text");
                    String city1 = distance_info.getJSONArray("destination_addresses").getString(0);
                    String city = distance_info.getJSONArray("origin_addresses").getString(0);

                    r = "Distance between " + city + " and " + city1 + " is " + location_distance;
                    TTS.speak("The Distance is "+ location_distance);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return r;
        }
        @Override
        protected void onPostExecute(String s) {
            if (s != null && !s.equals("")) {
                sendResponse(s);
            }
        }
    }



    public class NumberTest extends AsyncTask<URL, Void, String> {
        @Override
        protected String doInBackground(URL... urls) {
            URL searchUrl = urls[0];
            String number_result = null;
            try {
                number_result = getResponseFromHttpUrl(searchUrl);
                TTS.speak(number_result);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return number_result;
        }
        @Override
        protected void onPostExecute(String s) {
            if (s != null && !s.equals("")) {
                sendResponse(s);
            }
        }
    }


    public class NewsTest extends AsyncTask<URL, Void, String[]> {
        int len;
        @Override
        protected String[] doInBackground(URL... urls) {
            URL searchUrl = urls[0];
            String[] r = new String[20];
            String news_result = null;
            try {
                news_result = getResponseFromHttpUrl(searchUrl);
                try {
                    JSONObject news_info = new JSONObject(news_result);

                    JSONArray short_info = news_info.getJSONArray("articles");
                    len=short_info.length();
                    for(int i=0;i<len;i++) {

                        JSONObject short_info_desc = short_info.getJSONObject(i);
                        String tit = short_info_desc.getString("title");
                        String img = short_info_desc.getString("urlToImage");
                        String news_url = short_info_desc.getString("url");
                        r[i] = "Headline: " + tit + "\nLink : " + news_url;
                    }
                }
                catch (JSONException e) {
                    e.printStackTrace();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
            return r;
        }

        @Override
        protected void onPostExecute(String[] s) {
            if (s != null && !s.equals("")) {
                for(int i=0;i<len;i++)
                sendResponse(s[i]);
            }
        }
    }



    //Alarm
    public void createAlarm(String message, int hour, int minutes) {

        Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_MESSAGE, message)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minutes)
                .putExtra(AlarmClock.EXTRA_SKIP_UI,TRUE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }
    //Timer
    public void startTimer(String message, int seconds) {
        Intent intent = new Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_MESSAGE, message)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }
    }

    //call
    public String getPhoneNumber(String name, Context context) {
        String ret = null;
        String selection = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" like'%" + name +"%'";
        String[] projection = new String[] { ContactsContract.CommonDataKinds.Phone.NUMBER};
        Cursor c = context.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection, selection, null, null);
        if (c.moveToFirst()) {
            ret = c.getString(0);
        }
        c.close();
        if(ret==null)
            ret = "-1-1-1";
        return ret;
    }



    //custom tab
    @SuppressLint("ParcelCreator")
    public class CustomTabsURLSpan extends URLSpan {
        public CustomTabsURLSpan(String url) {
            super(url);
        }

        public CustomTabsURLSpan(Parcel src) {
            super(src);
        }

        @Override
        public void onClick(View widget) {
            String Url = getURL();

            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            builder.setStartAnimations(MainActivity.this, R.anim.slide_in_right, R.anim.slide_out_left);
            builder.setExitAnimations(MainActivity.this, R.anim.slide_in_left, R.anim.slide_out_right);
            CustomTabsIntent customTabsIntent = builder.build();
            customTabsIntent.launchUrl(MainActivity.this, Uri.parse(Url));

        }
    }
    public class LinkTransformationMethod implements TransformationMethod {

        @Override
        public CharSequence getTransformation(CharSequence source, View view) {
            if (view instanceof TextView) {
                TextView textView = (TextView) view;
                Linkify.addLinks(textView, Linkify.WEB_URLS);
                if (textView.getText() == null || !(textView.getText() instanceof Spannable)) {
                    return source;
                }
                Spannable text = (Spannable) textView.getText();
                URLSpan[] spans = text.getSpans(0, textView.length(), URLSpan.class);
                for (int i = spans.length - 1; i >= 0; i--) {
                    URLSpan oldSpan = spans[i];
                    int start = text.getSpanStart(oldSpan);
                    int end = text.getSpanEnd(oldSpan);
                    String url = oldSpan.getURL();
                    text.removeSpan(oldSpan);
                    text.setSpan(new CustomTabsURLSpan(url), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                return text;
            }
            return source;
        }

        @Override
        public void onFocusChanged(View view, CharSequence sourceText, boolean focused, int direction, Rect previouslyFocusedRect) {

        }
    }


    @Override
    public void onError(AIError error) { // here process error
        sendResponse(error.toString());
    }

    @Override
    public void onAudioLevel(float level) { // callback for sound level visualization

    }

    @Override
    public void onListeningStarted() { // indicate start listening here

    }

    @Override
    public void onListeningCanceled() { // indicate stop listening here

    }

    @Override
    public void onListeningFinished() { // indicate stop listening here

    }
}
