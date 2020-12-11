package com.grzegorzszawula.camerax;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Timer;
import java.util.TimerTask;

public class CameraService extends Service {

    //1. Kanał notyfikacji
    public static final String CHANNEL_ID = "ForegroundServiceChannel";
    public static final String CHANNEL_NAME = "CameraX service channel";

    //2. Odczyt danych zapisanych w Intent
    public static final String PHOTO_NUMBER = "photo_number";
    public static final String TIME_PERIOD = "time_period";

    private Integer photo_number, time_period;

    //4.
    private Context ctx;
    private Intent notificationIntent;
    private PendingIntent pendingIntent;

    //5.
    private int counter;
    private Timer timer;
    private TimerTask timerTask;
    final Handler handler = new Handler();


    private void sendStopBroadcast() {
        Intent intent = new Intent(MainActivity.RECEIVER_INTENT);
        intent.putExtra(MainActivity.RECEIVER_SERVICE_STOP, "RECEIVER_SERVICE_STOP");
        LocalBroadcastManager.getInstance(ctx).sendBroadcast(intent);
    }

    private void sendTakePhotoBroadcast() {
        Intent intent = new Intent(MainActivity.RECEIVER_INTENT);
        intent.putExtra(MainActivity.RECEIVER_TAKE_PHOTO, "RECEIVER_TAKE_PHOTO");
        intent.putExtra(MainActivity.RECEIVER_NUMBER, counter);
        LocalBroadcastManager.getInstance(ctx).sendBroadcast(intent);
    }

    final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            Log.w("TAG", "FOREGROUND SERVICE: runnable - start");
            Notification notification = new Notification.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_name)
                    .setShowWhen(true)
                    .setContentTitle("CameraX")
                    .setContentText("Photo number: " + String.valueOf(counter) + " of " + String.valueOf(photo_number))
                    .setContentIntent(pendingIntent)
                    .build();
            sendTakePhotoBroadcast();
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.notify(1, notification);
            counter++;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        ctx = this;
        notificationIntent = new Intent(ctx, MainActivity.class);
        pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(runnable);
        timer.cancel();
        timer.purge();
        timer = null;

        super.onDestroy();
        sendStopBroadcast();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        photo_number = intent.getIntExtra(PHOTO_NUMBER, 5);
        Log.w("TAG", "Photo number: " + photo_number.toString());

        time_period = intent.getIntExtra(TIME_PERIOD, 2000);
        Log.w("TAG", "Timer period: " + time_period.toString());

        counter = 1;
        Log.w("TAG", "Counter: " + String.valueOf(counter));

        createNotificationChannel();

        Notification notification = new Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setShowWhen(true)
                .setContentTitle("CameraX")
                .setContentText("Starting service to take " + String.valueOf(photo_number) + " photos"
                        + "\nTime period: " + time_period / 1000 + " s")
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);

        timer = new Timer();
        timerTask = new TimerTask() {
            @Override
            public void run() {
                if (counter <= photo_number) handler.post(runnable);
                else stopSelf();


            }
        };
        timer.schedule(timerTask, 0L, time_period);


        return START_NOT_STICKY;
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(serviceChannel);
    }


    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");

    }
}
