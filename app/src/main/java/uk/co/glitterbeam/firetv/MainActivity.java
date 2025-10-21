package uk.co.glitterbeam.firetv;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {

    private ExoPlayer player;
    private TextView tickerText;
    private Handler handler = new Handler();
    private Runnable tickerFetchTask;

    private static final String STREAM_URL = BuildConfig.STREAM_URL;
    private static final String LOGO_URL = BuildConfig.LOGO_URL;
    private static final String TICKER_URL = BuildConfig.TICKER_URL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ImageView logoView = findViewById(R.id.logoView);
        Button btnPlay = findViewById(R.id.btnPlay);
        Button btnPause = findViewById(R.id.btnPause);
        Button btnStop = findViewById(R.id.btnStop);
        tickerText = findViewById(R.id.tickerText);

        // Load logo
        Glide.with(this).load(LOGO_URL).into(logoView);

        // Initialize player lazily on first play
        btnPlay.setOnClickListener(v -> play());
        btnPause.setOnClickListener(v -> pause());
        btnStop.setOnClickListener(v -> stop());

        // Ensure marquee runs
        tickerText.setSelected(true);

        // Start periodic ticker refresh
        tickerFetchTask = new Runnable() {
            @Override
            public void run() {
                fetchAndApplyTicker();
                handler.postDelayed(this, 5 * 60 * 1000); // every 5 minutes
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tickerFetchTask);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tickerFetchTask);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
    }

    private void play() {
        if (player == null) {
            player = new ExoPlayer.Builder(this).build();
            MediaItem item = MediaItem.fromUri(Uri.parse(STREAM_URL));
            player.setMediaItem(item);
            player.prepare();
        }
        player.play();
    }

    private void pause() {
        if (player != null) player.pause();
    }

    private void stop() {
        releasePlayer();
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
    }

    private void fetchAndApplyTicker() {
        new Thread(() -> {
            try {
                URL url = new URL(TICKER_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("Accept", "application/json");
                int code = conn.getResponseCode();
                if (code == 200) {
                    InputStream is = conn.getInputStream();
                    String body = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                            .lines().collect(Collectors.joining("\n"));
                    JSONObject json = new JSONObject(body);
                    List<String> items = new ArrayList<>();
                    if (json.has("items")) {
                        JSONArray arr = json.getJSONArray("items");
                        for (int i = 0; i < arr.length(); i++) {
                            String s = arr.optString(i, "").trim();
                            if (!s.isEmpty()) items.add(s);
                        }
                    }
                    int speed = json.optInt("speed", 40);
                    String textColor = json.optString("textColor", "#FFFFFF");
                    String bgColor = json.optString("bgColor", "#160016");

                    String combined = String.join("    •    ", items);
                    runOnUiThread(() -> {
                        if (!combined.isEmpty()) tickerText.setText(combined);
                        try { tickerText.setTextColor(Color.parseColor(textColor)); } catch (Exception ignored) {}
                        try { tickerText.setBackgroundColor(Color.parseColor(bgColor)); } catch (Exception ignored) {}
                        // Note: 'speed' is not directly used by Android marquee; keeping for future custom scroller.
                    });
                } else {
                    Log.w("Ticker", "HTTP " + code);
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e("Ticker", "Failed to load ticker", e);
            }
        }).start();
    }
}
