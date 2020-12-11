package com.grzegorzszawula.camerax;

//Sprawy systemowe z JetPack-a

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.annotation.NonNull;

//Pozostałe sprawy systemowe
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

//Obsługa kamery z JetPack-a
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

//Obsługa plików z Java
import java.io.File;

//Obsługa wątków z Javy
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

//Obsługa watów z Androida
import com.google.common.util.concurrent.ListenableFuture;

public class MainActivity extends AppCompatActivity {

    //Nazwa pliku dla fotki
    private final String PHOTO_FILE_NAME = "mojaFotka";
    //Można użyć
    //new SimpleDateFormat("yyyy.MM.dd_HH:mm:ss")
    private final String PHOTO_FILE_EXTENSION = ".jpg";
    private String received_number;

    //Obsługa uprawień
    private final int REQUEST_CODE_PERMISSIONS = 10;
    private final String REQUIRED_PERMISSIONS_CAMERA = Manifest.permission.CAMERA;
    private final String REQUIRED_PERMISSIONS_WRITE = Manifest.permission.WRITE_EXTERNAL_STORAGE;

    public static final String RECEIVER_INTENT = "RECEIVER_INTENT";
    public static final String RECEIVER_MESSAGE = "RECEIVER_MESSAGE";
    public static final String RECEIVER_NUMBER = "RECEIVER_NUMBER";
    public static final String RECEIVER_TAKE_PHOTO = "RECEIVER_TAKE_PHOTO";
    public static final String RECEIVER_SERVICE_STOP = "RECEIVER_SERVICE_STOP";
    BroadcastReceiver broadcastReceiver;

    //Podczepienie widoków
    PreviewView previewView;
    Button takeButton, startService, stopService;

    //Obsługa kamery
    private ExecutorService cameraExecutor;
    private ImageCapture imageCapture;

    //Obsługa systemu plików
    private File outputDirectory;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setupUI();
        setupListeners();
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.hasExtra(RECEIVER_MESSAGE))
                    Log.w("TAG", "MAIN: " + intent.getStringExtra(RECEIVER_MESSAGE));
                if (intent.hasExtra(RECEIVER_NUMBER)) {
                    Log.w("TAG", "MAIN: " + String.valueOf(intent.getIntExtra(RECEIVER_NUMBER, 0)));
                    received_number = String.valueOf(intent.getIntExtra(RECEIVER_NUMBER, 0));
                }
                if (intent.hasExtra(RECEIVER_TAKE_PHOTO)) {
                    takePhoto();
                }
                if (intent.hasExtra(RECEIVER_SERVICE_STOP)) {
                    received_number = "";
                    updateUI();
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver((broadcastReceiver),
                new IntentFilter(RECEIVER_INTENT)
        );
        cameraExecutor = Executors.newSingleThreadExecutor();
        outputDirectory = getOutputDirectory();
        updateUI();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.itemSettings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case R.id.itemExit:
                finishAndRemoveTask();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
        cameraExecutor.shutdown();
    }


    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            startCamera();
        } else {
            Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private boolean allPermissionsGranted() {
        int permissionGranted = PackageManager.PERMISSION_GRANTED;
        int permissionRequiredCamera = ContextCompat.checkSelfPermission(getBaseContext(), REQUIRED_PERMISSIONS_CAMERA);
        int permissionRequiredWrite = ContextCompat.checkSelfPermission(getBaseContext(), REQUIRED_PERMISSIONS_WRITE);
        if (permissionRequiredWrite == permissionGranted) {
            return permissionRequiredCamera == permissionGranted;
        } else {
            return false;
        }
    }

    public void clickStart(View view) {
        //get preferences
        int time_multiplier = 1000; // 1 - milliseconds, 1000 - seconds
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        Integer photo_number = Integer.parseInt(sharedPreferences.getString("photo_number", "5"));
        Integer time_period = Integer.parseInt(sharedPreferences.getString("time_period", "2")) * time_multiplier;
        Intent startIntent = new Intent(this, CameraService.class);
        startIntent.putExtra(CameraService.PHOTO_NUMBER, photo_number);
        startIntent.putExtra(CameraService.TIME_PERIOD, time_period);
        ContextCompat.startForegroundService(this, startIntent);
        updateUI();
    }

    public void clickStop(View view) {
        Intent stopIntent = new Intent(this, CameraService.class);
        stopService(stopIntent);
        updateUI();

    }

    private void setupListeners() {
        takeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takePhoto();
            }
        });

        startService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickStart(v);
            }
        });
        stopService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clickStop(v);
            }
        });
    }

    private void setupUI() {
        received_number = "";
        previewView = findViewById(R.id.viewFinder);
        takeButton = findViewById(R.id.camera_capture_button);
        startService = findViewById(R.id.start_service);
        stopService = findViewById(R.id.stop_service);

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{REQUIRED_PERMISSIONS_CAMERA, REQUIRED_PERMISSIONS_WRITE},
                    REQUEST_CODE_PERMISSIONS);
        }
    }

    private void updateUI() {
        if (isMyForegroundServiceRunning()) {
            startService.setEnabled(false);
            stopService.setEnabled(true);
            takeButton.setEnabled(false);
        } else {
            startService.setEnabled(true);
            stopService.setEnabled(false);
            takeButton.setEnabled(true);
        }
    }

    private boolean isMyForegroundServiceRunning() {

        String myServiceName = CameraService.class.getName();
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        for (ActivityManager.RunningServiceInfo runningService : activityManager.getRunningServices(Integer.MAX_VALUE)) {
            String runningServiceName = runningService.service.getClassName();
            if (runningServiceName.equals(myServiceName)) {
                return true;
            }
        }
        return false;
    }

    private File getOutputDirectory() {
        File[] files = getExternalMediaDirs();
        return files[0];
//        File files = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
//        return files;
    }

    private void startCamera() {
        ListenableFuture cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = (ProcessCameraProvider) cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        //.requireLensFacing(lensFacing)
                        .build();
                Camera camera = cameraProvider.bindToLifecycle(
                        ((LifecycleOwner) this),
                        cameraSelector,
                        preview,
                        imageCapture);
                preview.setSurfaceProvider(
                        previewView.getSurfaceProvider());
            } catch (InterruptedException | ExecutionException e) {
                //
            }
        }, ContextCompat.getMainExecutor(this));
    }


    private void takePhoto() {
        //
        if (imageCapture == null) return;
        String file_name = PHOTO_FILE_NAME + received_number + PHOTO_FILE_EXTENSION;
        File photoFile = new File(outputDirectory, file_name);
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();
        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Uri savedUri = Uri.fromFile(photoFile);
                String msg = "Photo capture succeeded: " + savedUri;
                Toast.makeText(getBaseContext(), msg, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Toast.makeText(getBaseContext(), "Faild do save picture!", Toast.LENGTH_SHORT).show();
            }
        });
    }
}