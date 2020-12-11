package com.grzegorzszawula.camerax;

//Sprawy systemowe z JetPack-a

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.annotation.NonNull;

//Pozostałe sprawy systemowe
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
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
    private final String PHOTO_FILE_NAME = "mojaFotka.jpg";

    //Obsługa uprawień
    private final int REQUEST_CODE_PERMISSIONS = 10;
    private final String REQUIRED_PERMISSIONS_CAMERA = Manifest.permission.CAMERA;
    private final String REQUIRED_PERMISSIONS_WRITE = Manifest.permission.WRITE_EXTERNAL_STORAGE;

    //Podczepienie widoków
    PreviewView previewView;
    Button takeButton;

    //Obsługa kamery
    private ExecutorService cameraExecutor;
    private ImageCapture imageCapture;

    //Obsługa systemu plików
    private File outputDirectory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.viewFinder);
        takeButton = findViewById(R.id.camera_capture_button);

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{REQUIRED_PERMISSIONS_CAMERA, REQUIRED_PERMISSIONS_WRITE}, REQUEST_CODE_PERMISSIONS);
        }

        takeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takePhoto();
            }
        });

        cameraExecutor = Executors.newSingleThreadExecutor();
        outputDirectory = getOutputDirectory();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
        if (permissionRequiredWrite == permissionGranted){
            return permissionRequiredCamera == permissionGranted;
        }else{
              return false;
        }

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

        File photoFile = new File(outputDirectory, PHOTO_FILE_NAME);

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