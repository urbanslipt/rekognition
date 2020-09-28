package com.example.rekognition;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.rekognition.AmazonRekognition;
import com.amazonaws.services.rekognition.AmazonRekognitionClient;
import com.amazonaws.services.rekognition.AmazonRekognitionClientBuilder;
import com.amazonaws.services.rekognition.model.DetectLabelsRequest;
import com.amazonaws.services.rekognition.model.DetectLabelsResult;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.services.rekognition.model.Label;
import com.amazonaws.util.IOUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

import static android.icu.util.ULocale.getName;

public class MainActivity extends AppCompatActivity {

    Button btnCamera, btnUploadFile;
    private EditText etSearchFile, etUploadFile;

    private static final int CAMERA_PERMISSION_CODE = 250;
    private static final int CAMERA_REQUEST = 2;
    private static final int FILE_MANAGER_REQUEST = 1;

    private Uri fileUri, outputFileUri;
    private File photo;
    private String photoName;
    AmazonRekognition client;

    DetectLabelsRequest labelsRequest;

    BasicAWSCredentials credentials = new BasicAWSCredentials("AKIA4TOA6DU5BNILQP66", "jNTmdwmdwI0D/+DcnEebk/JHuruI3OePaBr122qE");

    final int MAX_LABELS = 10;
    final float MIN_CONFIDENCE = 75F;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //btnCamera.findViewById(R.id.imgBtnSelectFile);
        //btnUploadFile = findViewById(R.id.btnUploadFile);
        etUploadFile = findViewById(R.id.etUploadFile);
        etSearchFile = findViewById(R.id.etSearchFile);

//        btnUploadFile.setOnClickListener(view -> {
//            try {
//                uploadFile();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        });

//        btnCamera.setOnClickListener(this::openCamera);

//        client = AmazonRekognitionClientBuilder.standard()
//                .withRegion("us-east-1")
//                .withCredentials(new AWSStaticCredentialsProvider(credentials)).build();


        client = new AmazonRekognitionClient(new BasicAWSCredentials("AKIA4TOA6DU5BNILQP66", "jNTmdwmdwI0D/+DcnEebk/JHuruI3OePaBr122qE"));

        labelsRequest = new DetectLabelsRequest();

    }

    private LabelsResponse detectLabels(){

        return null;
    }

    ByteBuffer sourceImageBytes;
    private void uploadFile() throws IOException {
        InputStream sourceImage = getContentResolver().openInputStream(fileUri);
        sourceImageBytes = ByteBuffer.wrap(IOUtils.toByteArray(sourceImage));

        labelsRequest.setImage(new Image().withBytes(sourceImageBytes));
        labelsRequest.setMaxLabels(MAX_LABELS);
        labelsRequest.setMinConfidence(MIN_CONFIDENCE);

        DetectLabelsResult result = client.detectLabels(labelsRequest);
        List<Label> labels = result.getLabels();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_MANAGER_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            clear();
            fileUri = data.getData();
            etUploadFile.setText(getFileName(this, fileUri));
        } else if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK ) {
            clear();
            File file = new File(getExternalFilesDir("esco_images"), photoName);
            outputFileUri = FileProvider.getUriForFile(this, this.getApplicationContext().getPackageName() + ".provider", file);
            etUploadFile.setText(getFileName(this, outputFileUri));
        }
    }

    private void openCamera(View view) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        } else {
            photoName = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) +".jpg";
            Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            File tempFile = new File(getExternalFilesDir("esco_images"), photoName);
            Uri uri = FileProvider.getUriForFile(this, this.getApplicationContext().getPackageName() + ".provider", tempFile);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            startActivityForResult(cameraIntent, CAMERA_REQUEST);
        }
    }

    private void clear(){
        fileUri = null;
        photo = null;
        etUploadFile.setText("");
    }

    public static String getFileName(@NonNull Context context, Uri uri) {
        String mimeType = context.getContentResolver().getType(uri);
        String filename = null;

        if (mimeType == null) {
            String path = getPath(context, uri);
            if (path == null) {
                filename = getName(uri.toString());
            } else {
                File file = new File(path);
                filename = file.getName();
            }
        } else {
            Cursor returnCursor = context.getContentResolver().query(uri, null,
                    null, null, null);
            if (returnCursor != null) {
                int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                returnCursor.moveToFirst();
                filename = returnCursor.getString(nameIndex);
                returnCursor.close();
            }
        }

        return filename;
    }

    public static String getPath(final Context context, Uri uri) {
        // DocumentProvider
        if (DocumentsContract.isDocumentUri(context, uri)) {

            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/"
                            + split[1];
                }
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {
                return "/storage/emulated/0/Download/"+getFileName(context, uri);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[] { split[1] };

                return getDataColumn(context, contentUri, selection,
                        selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {

            // Return the remote address
            if (isGooglePhotosUri(uri))
                return uri.getLastPathSegment();

            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri
                .getAuthority());
    }

    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri
                .getAuthority());
    }

    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri
                .getAuthority());
    }

    public static String getDataColumn(Context context, Uri uri,
                                       String selection, String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = { column };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri
                .getAuthority());
    }

    public static MultipartBody.Part prepareFilePart(@NonNull Context context, @NonNull String partName, @NonNull Uri fileUri) {
        File file = new File(getPath(context, fileUri));
        RequestBody requestFile = RequestBody.create(file, MediaType.parse(context.getContentResolver().getType(fileUri)));
        return MultipartBody.Part.createFormData(partName, file.getAbsolutePath(), requestFile);
    }

}