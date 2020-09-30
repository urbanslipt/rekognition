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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.rekognition.AmazonRekognitionClient;
import com.amazonaws.services.rekognition.model.BoundingBox;
import com.amazonaws.services.rekognition.model.DetectLabelsRequest;
import com.amazonaws.services.rekognition.model.DetectLabelsResult;
import com.amazonaws.services.rekognition.model.Image;
import com.amazonaws.util.IOUtils;
import com.amplifyframework.AmplifyException;
import com.amplifyframework.auth.cognito.AWSCognitoAuthPlugin;
import com.amplifyframework.core.Amplify;
import com.amplifyframework.predictions.aws.AWSPredictionsEscapeHatch;
import com.amplifyframework.predictions.aws.AWSPredictionsPlugin;
import com.amplifyframework.predictions.models.Label;
import com.amplifyframework.predictions.models.LabelType;
import com.amplifyframework.predictions.result.IdentifyLabelsResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

import static android.icu.util.ULocale.getName;

public class MainActivity extends AppCompatActivity {

    private Button  btnUploadFile;
    private EditText etSearchFile, etUploadFile;
    private ImageButton btnCamera;
    private WebView webViewJson;

    private static final int CAMERA_PERMISSION_CODE = 250;
    private static final int CAMERA_REQUEST = 2;
    private static final int FILE_MANAGER_REQUEST = 1;

    private Uri fileUri, outputFileUri;
    private File photo;
    private String photoName;

    AmazonRekognitionClient client;
    AWSPredictionsEscapeHatch escapeHatch;
    CognitoCachingCredentialsProvider credentialsProvider;

    private final MediaPlayer mp = new MediaPlayer();

    ImageView imageViewCanvas;

    final int MAX_LABELS = 10;
    final float MIN_CONFIDENCE = 75F;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (android.os.Build.VERSION.SDK_INT > 9)
        {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        btnCamera = findViewById(R.id.imgBtnSelectFile);
        btnUploadFile = findViewById(R.id.btnUploadFile);
        etUploadFile = findViewById(R.id.etUploadFile);
        imageViewCanvas = findViewById(R.id.imageViewCanvas);
        webViewJson = findViewById(R.id.webViewJson);

        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);
        paint.setColor(Color.BLUE);

        //canvas.drawFrame();

        btnUploadFile.setOnClickListener(view -> {
            try {
                uploadFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        btnCamera.setOnClickListener(this::openFileManager);

//        client = AmazonRekognitionClientBuilder.standard()
//                .withRegion("us-east-1")
//                .withCredentials(new AWSStaticCredentialsProvider(credentials)).build();


//        client = AmazonRekognitionClientBuilder.standard().setCredentials(credentials);



//        client = SqsClient.builder().httpClient(UrlConnectionHttpClient.create())
//                .region(Region.AP_EAST_1)
//                .credentialsProvider()

//        labelsRequest = new DetectLabelsRequest();

        // Inicializar el proveedor de credenciales de Amazon Cognito
        credentialsProvider = new CognitoCachingCredentialsProvider(
                getApplicationContext(),
                "us-east-1:62f10681-64aa-4b28-9971-1c36080832c5", // ID del grupo de identidades
                Regions.US_EAST_1 // Región
        );

        try {
            Amplify.addPlugin(new AWSCognitoAuthPlugin());
            Amplify.addPlugin(new AWSPredictionsPlugin());
            Amplify.addPlugin(new AWSCognitoAuthPlugin());
            Amplify.configure(getApplicationContext());

//            Amplify.Auth.signUp(
//                    "jvelazquez@esco.mx",
//                    "Password123",
//                    AuthSignUpOptions.builder().userAttribute(AuthUserAttributeKey.email(), "jvelazquez@esco.mx").build(),
//                    result -> Log.i("AuthQuickStart", "Result: " + result.toString()),
//                    error -> Log.e("AuthQuickStart", "Sign up failed", error)
//            );

//            Amplify.Auth.confirmSignUp(
//                    "jvelazquez@esco.mx",
//                    "629426",
//                    result -> Log.i("AuthQuickstart", result.isSignUpComplete() ? "Confirm signUp succeeded" : "Confirm sign up not complete"),
//                    error -> Log.e("AuthQuickstart", error.toString())
//            );

            Amplify.Auth.signIn(
                    "jvelazquez@esco.mx",
                    "Password123",
                    result -> Log.i("AuthQuickstart", result.isSignInComplete() ? "Sign in succeeded" : "Sign in not complete"),
                    error -> Log.e("AuthQuickstart", error.toString())
            );

            Log.i("MyAmplifyApp", "Initialized Amplify");
        } catch (AmplifyException error) {
            Log.e("MyAmplifyApp", "Could not initialize Amplify", error);
        }

        AWSPredictionsPlugin predictionsPlugin = (AWSPredictionsPlugin)
                Amplify.Predictions.getPlugin("awsPredictionsPlugin");
        escapeHatch = predictionsPlugin.getEscapeHatch();


        Amplify.Predictions.convertTextToSpeech("Me gusta la pizza", result -> playAudio(result.getAudioData()),
                error -> Log.e("MyAmplifyApp", "Conversion failed", error));

    }


    private void playAudio(InputStream data) {
        File mp3File = new File(getCacheDir(), "audio.mp3");

        try (OutputStream out = new FileOutputStream(mp3File)) {
            byte[] buffer = new byte[8 * 1_024];
            int bytesRead;
            while ((bytesRead = data.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            mp.reset();
            mp.setOnPreparedListener(MediaPlayer::start);
            mp.setDataSource(new FileInputStream(mp3File).getFD());
            mp.prepareAsync();
        } catch (IOException error) {
            Log.e("MyAmplifyApp", "Error writing audio file", error);
        }
    }


    ByteBuffer sourceImageBytes;
    private void uploadFile() throws IOException {

        client = escapeHatch.getRekognitionClient();

        InputStream sourceImage = getContentResolver().openInputStream(fileUri);
        sourceImageBytes = ByteBuffer.wrap(IOUtils.toByteArray(sourceImage));



        DetectLabelsRequest labelsRequest = new DetectLabelsRequest();

        labelsRequest.setImage(new Image().withBytes(sourceImageBytes));
        labelsRequest.setMaxLabels(MAX_LABELS);
        labelsRequest.setMinConfidence(MIN_CONFIDENCE);
        DetectLabelsResult resultJson = client.detectLabels(labelsRequest);
        List<com.amazonaws.services.rekognition.model.Label> labels = resultJson.getLabels();


        Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), fileUri);
        Bitmap mBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

        Canvas canvas = new Canvas(mBitmap);
        float left = 0;
        float top = 0;
        int height = canvas.getHeight();
        int width = canvas.getWidth();
        int scale = 1;

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.BLACK);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5f);

        for (com.amazonaws.services.rekognition.model.Label lbl : labels){
            if (lbl.getInstances().size() > 0){
                BoundingBox boundingBox = lbl.getInstances().get(0).getBoundingBox();
                left = width * boundingBox.getLeft();
                top = height * boundingBox.getTop();
                canvas.drawRect(Math.round(left / scale), Math.round(top / scale),
                        Math.round((width * boundingBox.getWidth()) / scale), Math.round((height * boundingBox.getHeight())) / scale, paint);
            }
        }

        imageViewCanvas.setImageBitmap(mBitmap);

        //canvas.setCanvas(mBitmap);

        Amplify.Predictions.identify(LabelType.LABELS, bitmap, result -> {
            IdentifyLabelsResult identifyResult = (IdentifyLabelsResult) result;
            Label label = identifyResult.getLabels().get(0);
            Log.i("MyAmplifyApp", label.getName());
            }, error -> Log.e("MyAmplifyApp", "Label detection failed", error));
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

    private void openFileManager(View v) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(Intent.createChooser(intent, "file"), 1);
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