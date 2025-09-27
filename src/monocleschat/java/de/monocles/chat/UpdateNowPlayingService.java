package de.monocles.chat;

import static eu.siacs.conversations.receiver.SystemEventReceiver.EXTRA_NEEDS_FOREGROUND_SERVICE;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.services.XmppConnectionService;

public class UpdateNowPlayingService extends NotificationListenerService {

    private static final String TAG = "UpdateNowPlayingService";

    public XmppConnectionService xmppConnectionService = null;
    protected ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            XmppConnectionService.XmppConnectionBinder binder = (XmppConnectionService.XmppConnectionBinder) service;
            xmppConnectionService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            xmppConnectionService = null;
        }
    };

    @Override
    public void onCreate() {
        // From XmppActivity.connectToBackend
        Intent intent = new Intent(this, XmppConnectionService.class);
        intent.setAction(XmppConnectionService.ACTION_STARTING_CALL);
        intent.putExtra(EXTRA_NEEDS_FOREGROUND_SERVICE, true);
        try {
            startService(intent);
        } catch (IllegalStateException e) {
            Log.w(TAG, "unable to start service from " + getClass().getSimpleName());
        }
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        queryActiveSessions();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        queryActiveSessions();
    }

    private void queryActiveSessions() {
        if (xmppConnectionService == null) {
            return;
        }

        MediaSessionManager mediaSessionManager =
                (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        ComponentName component = new ComponentName(this, UpdateNowPlayingService.class);

        Context context = getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!prefs.getBoolean("load_now_playing_from_system", false)) {
            return;
        }

        int track_longer_than = Integer.parseInt(prefs.getString("update_track_longer_than",
                String.valueOf(context.getResources().getInteger(R.integer.update_track_longer_than_secs))));

        List<MediaController> controllers;
        try {
            controllers = mediaSessionManager.getActiveSessions(component);
        } catch (SecurityException e) {
            Log.e(TAG, "Notification access not granted");
            return;
        }

        for (MediaController controller : controllers) {
            MediaMetadata metadata = controller.getMetadata();
            PlaybackState playbackState = controller.getPlaybackState();

            if (playbackState != null && playbackState.getState() == PlaybackState.STATE_PLAYING) {

                if (metadata == null) {
                    continue;
                }

                long duration_secs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION) / 1000;
                if (duration_secs <= track_longer_than) {
                    continue;
                }

                if (xmppConnectionService.publishUserTuneAsync(metadata)) {
                    break;
                }
            }
        }
    }
}
