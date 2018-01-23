package com.burlap.filetransfer;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.zhy.http.okhttp.request.CountingRequestBody;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FileTransferModule extends ReactContextBaseJavaModule {

    private final OkHttpClient client = new OkHttpClient();
    private String TAG = "ImageUploadAndroid";
    ReactApplicationContext reactContext = null;

    public FileTransferModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "RNUploader";
    }

    @ReactMethod
    public void upload(ReadableMap options, Callback complete) {

        final Callback completeCallback = complete;

        try {
            MultipartBody.Builder mRequestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM);

            ReadableArray files = options.getArray("files");
            String url = options.getString("url");

            if (options.hasKey("params")) {
                ReadableMap data = options.getMap("params");
                ReadableMapKeySetIterator iterator = data.keySetIterator();

                while (iterator.hasNextKey()) {
                    String key = iterator.nextKey();
                    if (ReadableType.String.equals(data.getType(key))) {
                        mRequestBody.addFormDataPart(key, data.getString(key));
                    }
                }
            }

            if (files.size() != 0) {
                for (int fileIndex = 0; fileIndex < files.size(); fileIndex++) {
                    ReadableMap file = files.getMap(fileIndex);
                    String uri = file.getString("filepath");

                    Uri file_uri;
                    if (uri.substring(0, 10).equals("content://")) {
                        file_uri = Uri.parse(convertMediaUriToPath(Uri.parse(uri)));
                    } else {
                        file_uri = Uri.parse(uri);
                    }

                    File imageFile = new File(file_uri.getPath());
                    File compressedFile = saveBitmapToFile(imageFile);

                    if (compressedFile == null) {
                        Log.d(TAG, "FILE NOT FOUND");
                        completeCallback.invoke("FILE NOT FOUND", null);
                        return;
                    }

                    String mimeType = "image/png";
                    if (file.hasKey("filetype")) {
                        mimeType = file.getString("filetype");
                    }
                    MediaType mediaType = MediaType.parse(mimeType);
                    String fileName = file.getString("filename");
                    String name = fileName;
                    if (file.hasKey("name")) {
                        name = file.getString("name");
                    }


                    mRequestBody.addFormDataPart(name, compressedFile.getName(), RequestBody.create(mediaType, compressedFile));
                }
            }


            MultipartBody requestBody = mRequestBody.build();

            CountingRequestBody monitoredRequest = new CountingRequestBody(requestBody, new CountingRequestBody.Listener() {
                @Override
                public void onRequestProgress(long bytesWritten, long contentLength) {
                    WritableMap params = Arguments.createMap();
                    params.putDouble("progress", 100f * bytesWritten / contentLength);
                    params.putDouble("totalBytesWritten", bytesWritten);
                    params.putDouble("totalBytesExpectedToWrite", contentLength);

                    reactContext
                            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                            .emit("RNUploaderProgress", params);
                }
            });

            Request.Builder request = new Request.Builder()
                    .header("Content-Type", "multipart/form-data")
                    .url(url)
                    .post(monitoredRequest);

            if (options.hasKey("headers")) {
                ReadableMap data = options.getMap("headers");
                ReadableMapKeySetIterator iterator = data.keySetIterator();

                while (iterator.hasNextKey()) {
                    String key = iterator.nextKey();
                    if (ReadableType.String.equals(data.getType(key))) {
                        request.addHeader(key, data.getString(key));
                    }
                }
            }

            Request finalRequest = request.build();

            Response response = client.newCall(finalRequest).execute();
            if (!response.isSuccessful()) {
                Log.d(TAG, "Unexpected code" + response.toString());
                completeCallback.invoke(response.body().string(), null);
                return;
            }

            completeCallback.invoke(null, response.body().string());
        } catch (Exception e) {
            Log.d(TAG, e.toString());
        }
    }

    private String convertMediaUriToPath(Uri uri) {
        Context context = getReactApplicationContext();
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = context.getContentResolver().query(uri, proj, null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        String path = cursor.getString(column_index);
        cursor.close();
        return path;
    }

    private File saveBitmapToFile(File file){
        try {

            // BitmapFactory options to downsize the image
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            o.inSampleSize = 6;
            // factor of downsizing the image

            FileInputStream inputStream = new FileInputStream(file);
            //Bitmap selectedBitmap = null;
            BitmapFactory.decodeStream(inputStream, null, o);
            inputStream.close();

            // The new size we want to scale to
            final int REQUIRED_SIZE=75;

            // Find the correct scale value. It should be the power of 2.
            int scale = 1;
            while(o.outWidth / scale / 2 >= REQUIRED_SIZE &&
                    o.outHeight / scale / 2 >= REQUIRED_SIZE) {
                scale *= 2;
            }

            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = scale;
            inputStream = new FileInputStream(file);

            Bitmap selectedBitmap = BitmapFactory.decodeStream(inputStream, null, o2);
            inputStream.close();

            // here i override the original image file
            file.createNewFile();
            FileOutputStream outputStream = new FileOutputStream(file);

            selectedBitmap.compress(Bitmap.CompressFormat.JPEG, 75 , outputStream);

            return file;
        } catch (Exception e) {
            return null;
        }
    }
}
