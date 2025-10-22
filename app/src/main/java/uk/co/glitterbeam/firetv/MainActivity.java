
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

        Glide.with(this).load(LOGO_URL).into(logoView);

        btnPlay.setOnClickListener(v -> play());
        btnPause.setOnClickListener(v -> pause());
        btnStop.setOnClickListener(v -> stop());

        tickerText.setSelected(true);

        tickerFetchTask = new Runnable() {
            @Override
            public void run() {
                fetchAndApplyTicker();
                handler.postDelayed(this, 5 * 60 * 1000);
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
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line).append('
');
                    }
                    JSONObject json = new JSONObject(sb.toString());
                    StringBuilder combined = new StringBuilder();
                    if (json.has("items")) {
                        JSONArray arr = json.getJSONArray("items");
                        for (int i = 0; i < arr.length(); i++) {
                            String s = arr.optString(i, "").trim();
                            if (!s.isEmpty()) {
                                if (combined.length() > 0) combined.append("    •    ");
                                combined.append(s);
                            }
                        }
                    }
                    final String textColor = json.optString("textColor", "#FFFFFF");
                    final String bgColor = json.optString("bgColor", "#160016");

                    runOnUiThread(() -> {
                        if (combined.length() > 0) tickerText.setText(combined.toString());
                        try { tickerText.setTextColor(Color.parseColor(textColor)); } catch (Exception ignored) {}
                        try { tickerText.setBackgroundColor(Color.parseColor(bgColor)); } catch (Exception ignored) {}
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
