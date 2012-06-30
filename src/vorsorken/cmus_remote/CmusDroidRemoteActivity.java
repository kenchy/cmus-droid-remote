package vorsorken.cmus_remote;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

public class CmusDroidRemoteActivity extends SherlockActivity {

    public enum CmusCommand {
        REPEAT("Repeat", "toggle repeat"), SHUFFLE("Shuffle", "toggle shuffle"), STOP(
                "Stop", "player-stop"), NEXT("Next", "player-next"), PREV(
                "Previous", "player-prev"), PLAY("Play", "player-play"), PAUSE(
                "Pause", "player-pause"),
        // FILE("player-play %s");
        // VOLUME("vol %s"),
        VOLUME_MUTE("Mute", "vol -100%"), VOLUME_UP("Volume +", "vol +10%"), VOLUME_DOWN(
                "Volume -", "vol -10%"),
        // SEEK("seek %s"),
        STATUS("Status", "status");

        private final String label;
        private final String command;

        private CmusCommand(String label, String command) {
            this.label = label;
            this.command = command;
        }

        public String getCommand() {
            return command;
        }

        public String getLabel() {
            return label;
        }

        @Override
        public String toString() {
            return getLabel();
        }
    }

    public static final String TAG = "CmusDroidRemoteActivity";

    // Settings text fields
    private EditText mHostText;
    private EditText mPortText;
    private EditText mPassText;

    // main buttons
    private Button pauseButton;
    private Button prevButton;
    private Button nextButton;
    private Button stopButton;
    private Button shuffleButton;
    private Button repeatButton;

    private TextView artistTV;
    private TextView trackTV;
    private TextView albumTV;
    private ProgressBar trackProgress;
    private TextView trackProgTV;

    private Dialog dialog;

    private Timer statusTimer;

    private String host;
    private String port;
    private String pass;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // Obtain handles to UI objects
        artistTV = (TextView) findViewById(R.id.artistTV);
        trackTV = (TextView) findViewById(R.id.trackTV);
        albumTV = (TextView) findViewById(R.id.albumTV);
        trackProgress = (ProgressBar) findViewById(R.id.trackProgress);
        trackProgress.setIndeterminate(false);
        trackProgTV = (TextView) findViewById(R.id.trackProgTV);

        if (!isUsingWifi()) {
            alert("Connect to Wifi", "Device must be connected to Wifi.");
        }
        
        initButtons();

        loadSettings();

        statusTimer = new Timer();
        statusTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    sendCommand(host, Integer.parseInt(port), pass,
                            CmusCommand.STATUS);
                } catch (NumberFormatException e) {
                    Log.e("statusTimer",
                            "couldn't parse int from port, probably not set yet");
                }
            }
        }, 0, 500);
    }

    private void initButtons() {
        pauseButton = (Button) findViewById(R.id.pauseButton);
        pauseButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCommand(host, Integer.parseInt(port), pass, CmusCommand.PAUSE);
            }
        });

        prevButton = (Button) findViewById(R.id.prevButton);
        prevButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCommand(host, Integer.parseInt(port), pass, CmusCommand.PREV);
            }
        });

        nextButton = (Button) findViewById(R.id.nextButton);
        nextButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCommand(host, Integer.parseInt(port), pass, CmusCommand.NEXT);
            }
        });

        stopButton = (Button) findViewById(R.id.stopButton);
        stopButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCommand(host, Integer.parseInt(port), pass, CmusCommand.STOP);
            }
        });

        shuffleButton = (Button) findViewById(R.id.shuffleButton);
        shuffleButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCommand(host, Integer.parseInt(port), pass, CmusCommand.SHUFFLE);
            }
        });

        repeatButton = (Button) findViewById(R.id.repeatButton);
        repeatButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCommand(host, Integer.parseInt(port), pass, CmusCommand.REPEAT);
            }
        });
    }

    private void loadSettings() {
        SharedPreferences pref = getPreferences(MODE_PRIVATE);
        host = pref.getString("host", "");
        port = pref.getString("port", "");
        pass = pref.getString("pass", "");

        if (host.isEmpty() || port.isEmpty()) {
            alert("Go to settings...", 
                    "Please navigate to the Settings page and enter your information.");
        }

    }

    private void alert(String title, String message) {
        new AlertDialog.Builder(this).setMessage(message).setTitle(title).show();
    }

    private void addTagOrSetting(CmusStatus cmusStatus, String line) {
        int firstSpace = line.indexOf(' ');
        int secondSpace = line.indexOf(' ', firstSpace + 1);
        String type = line.substring(0, firstSpace);
        String key = line.substring(firstSpace + 1, secondSpace);
        String value = line.substring(secondSpace + 1);
        if (type.equals("set")) {
            cmusStatus.setSetting(key, value);
        } else if (type.equals("tag")) {
            cmusStatus.setTag(key, value);
        } else {
            Log.e(TAG, "Unknown type in status: " + line);
        }
    }

    private void handleStatus(String status) {

        CmusStatus cmusStatus = new CmusStatus();

        String[] strs = status.split("\n");

        for (String str : strs) {
            if (str.startsWith("set") || str.startsWith("tag")) {
                addTagOrSetting(cmusStatus, str);
            } else {
                int firstSpace = str.indexOf(' ');
                String type = str.substring(0, firstSpace);
                String value = str.substring(firstSpace + 1);
                if (type.equals("status")) {
                    cmusStatus.setStatus(value);
                } else if (type.equals("file")) {
                    cmusStatus.setFile(value);
                } else if (type.equals("duration")) {
                    cmusStatus.setDuration(value);
                } else if (type.equals("position")) {
                    cmusStatus.setPosition(value);
                }
            }
        }

        artistTV.setText("Artist: " + cmusStatus.getTag("artist"));
        trackTV.setText("Track: " + cmusStatus.getTag("title"));
        albumTV.setText("Album: " + cmusStatus.getTag("album"));
        trackProgress.setProgress(cmusStatus.getProgress());
        trackProgTV.setText(cmusStatus.getRunTime());

    }

    private void sendCommand(final String host, final int port,
            final String password, final CmusCommand command) {

        new Thread(new Runnable() {
            private String readAnswer(BufferedReader in) throws IOException {
                StringBuilder answerBuilder = new StringBuilder();

                String line;
                while ((line = in.readLine()) != null && line.length() != 0) {
                    answerBuilder.append(line).append("\n");
                }

                return answerBuilder.toString();
            }

            private void handleCmdAnswer(BufferedReader in,
                    final CmusCommand command) throws Exception {
                final String cmdAnswer = readAnswer(in);
                if (cmdAnswer != null && cmdAnswer.trim().length() != 0) {
                    CmusDroidRemoteActivity.this.runOnUiThread(new Runnable() {
                        public void run() {
                            if (command.equals(CmusCommand.STATUS)) {
                                handleStatus(cmdAnswer);
                            } else {
                                alert("Message from Cmus", "Received message: "
                                        + cmdAnswer);
                            }
                        }
                    });
                }
            }

            private void validAuth(BufferedReader in) throws Exception {
                String passAnswer = readAnswer(in);
                if (passAnswer != null && passAnswer.trim().length() != 0) {
                    throw new Exception("Could not login: " + passAnswer);
                }
            }

            public void run() {
                Socket socket = null;
                BufferedReader in = null;
                PrintWriter out = null;
                try {
                    socket = new Socket(host, port);
                    // Log.v(TAG, "Connected to " + host + ":" + port);
                    in = new BufferedReader(new InputStreamReader(
                            socket.getInputStream()), Character.SIZE);
                    out = new PrintWriter(socket.getOutputStream(), true);

                    out.println("passwd " + password);
                    validAuth(in);
                    out.println(command.getCommand());
                    handleCmdAnswer(in, command);
                } catch (final Exception e) {
                    Log.e(TAG, "Could not send the command", e);
                    CmusDroidRemoteActivity.this.runOnUiThread(new Runnable() {
                        public void run() {
                            alert("Could not send command", "Could not send the command: " + e.getLocalizedMessage());
                        }
                    });
                } finally {
                    if (in != null) {
                        try {
                            in.close();
                        } catch (Exception e1) {
                        }
                        in = null;
                    }
                    if (out != null) {
                        try {
                            out.close();
                        } catch (Exception e1) {
                        }
                        out = null;
                    }
                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (Exception e) {
                        }
                        socket = null;
                    }
                }
            }
        }).start();
    }

    private boolean isUsingWifi() {
        ConnectivityManager connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return mWifi.isConnected();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.settings:
            initDialog();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    public void initDialog() {
        dialog = new Dialog(this);
        dialog.show();
        dialog.setContentView(R.layout.settings_dialog);
        dialog.setTitle("Settings");

        mHostText = (EditText) dialog.findViewById(R.id.hostText);
        mPortText = (EditText) dialog.findViewById(R.id.portText);
        mPassText = (EditText) dialog.findViewById(R.id.passwordText);

        Button okButton = (Button) dialog.findViewById(R.id.okButton);
        okButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences pref = getPreferences(MODE_PRIVATE);
                SharedPreferences.Editor editor = pref.edit();
                editor.putString("host", mHostText.getText().toString());
                editor.putString("port", mPortText.getText().toString());
                editor.putString("pass", mPassText.getText().toString());
                editor.commit();
                dialog.dismiss();
                loadSettings();
            }
        });

        Button cancelButton = (Button) dialog.findViewById(R.id.cancelButton);
        cancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        statusTimer.cancel();
    }

}