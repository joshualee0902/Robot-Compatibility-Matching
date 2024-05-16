package com.joshualee0902.myapplication;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;


public class Screen6 extends AppCompatActivity {

    Button submit;
    TextView final_product;
    TextView format;
    public Database myDb = new Database(this);
    StringBuffer buffer;
    Button home;
    Button bluetooth;
    Button chooseFile;
    public static String resText;
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int DISCOVER_DURATION = 300;
    private static final int REQUEST_BLU = 1;

    String path;

    private static final String[] INITIAL_PERMS = {Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION};

    private static final int INITIAL_REQUEST = 1337;

    private static final int REQUEST_WRITE_STORAGE = INITIAL_REQUEST + 4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen6);

        format = findViewById(R.id.format);

        submit = findViewById(R.id.next4);
        submit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                submitData();
                makeTxt();
            }
        });

        home = findViewById(R.id.home);
        home.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openHome();
            }
        });

        if (!canAccessLocation() || !canAccessCamera() || !canAccessWriteStorage() || !canAccessReadStorage() || !canAccessReadContacts() || !canAccessWriteContacts()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(INITIAL_PERMS, INITIAL_REQUEST);
            }
        }

    }

    public void submitData() {
        boolean isInserted = myDb.insertData(Screen2.teamNumber, Screen2.matchNumber, Screen5.windrawlose, Screen3.didCrossLine, Screen3.autoUpper, Screen3.autoLower, Screen4.teleUpper, Screen4.teleLower, Screen4.wheel, Screen4.climbed, Screen4.fouls, Screen5.totPoints, Screen5.rankingPoints, Screen5.bricked, Screen5.say);
        if (isInserted) {
            //if the data is successfully submitted, "Data Inserted" Toast will appear
            Toast.makeText(Screen6.this, "Data Inserted", Toast.LENGTH_LONG).show();
        } else {
            //if the data is not successfully submitted, "Data Not Inserted" Toast will appear
            Toast.makeText(Screen6.this, "Data Not Inserted", Toast.LENGTH_LONG).show();
        }

        Cursor res = myDb.getAllData();
        if (res.getCount() == 0) {
            Toast.makeText(Screen6.this, "Nothing is found.", Toast.LENGTH_SHORT).show();
        }

        buffer = new StringBuffer();
        while (res.moveToNext()) {
            buffer.append(res.getString(0) + "|" + res.getString(1) + "|" + res.getString(2) + "|" + res.getString(3) + "|" + res.getString(4) +
                    "|" + res.getString(5) + "|" + res.getString(6) + "|" + res.getString(7) + "|" + res.getString(8) +
                    "|" + res.getString(9) + "|" + res.getString(11) + "|" + res.getString(12) + "|" + res.getString(13) + "|" + res.getString(14) + "\n");
        }

        resText = buffer.toString();
        final_product = findViewById(R.id.final_product);
        final_product.setText(resText);
        final_product.setFocusable(true);
        final_product.setEnabled(true);
        final_product.setClickable(true);
        final_product.setFocusableInTouchMode(true);

    }

     public void makeTxt() {
         if (!resText.isEmpty()) {
             String state = Environment.getExternalStorageState();
             if (Environment.MEDIA_MOUNTED.equals(state)) {
                 if (Build.VERSION.SDK_INT >= 23) {
                     if (checkPermission()) {
                         File sdcard = Environment.getExternalStorageDirectory();
                         File dir = new File(sdcard.getAbsolutePath() + "/text files/");
                         dir.mkdir();
                         File file = new File(dir, "data.txt");
                         FileOutputStream os = null;
                         try {
                             os = new FileOutputStream(file);
                             os.write(resText.getBytes());
                             os.close();
                         } catch (IOException e) {
                             e.printStackTrace();
                         }
                     } else {
                         requestPermission();
                     }
                 } else {

                     File sdcard = Environment.getExternalStorageDirectory();
                     File dir = new File(sdcard.getAbsolutePath() + "/text files/");
                     dir.mkdir();
                     File file = new File(dir, "data.txt");
                     FileOutputStream os = null;
                     try {
                         os = new FileOutputStream(file);
                         os.write(resText.getBytes());
                         os.close();
                     } catch (IOException e) {
                         e.printStackTrace();
                     }
                 }


             }


         }

     }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

        switch (requestCode) {
            case REQUEST_WRITE_STORAGE:
                if (canAccessWriteStorage()) {
                    //reload my activity with permission granted or use the features what required the permission
                    System.out.println("Permission Granted");

                } else {
                    Toast.makeText(this, "The app was not allowed to write to your storage. Hence, it cannot function properly. Please consider granting it this permission", Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    public void sendViaBluetooth(View v) {
        if (path == null) {
            Toast.makeText(this, "Please select file first", Toast.LENGTH_SHORT).show();
            return;
        }

        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();

        if (btAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_LONG).show();
        } else {
            enableBluetooth();
        }
    }

    public void getFile(View v) {
        Intent mediaIntent = new Intent(Intent.ACTION_GET_CONTENT);
        mediaIntent.setType("*/*"); //set mime type as per requirement
        startActivityForResult(mediaIntent, 1001);
    }

    public void enableBluetooth() {
        Intent discoveryIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        final Intent intent = discoveryIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVER_DURATION);
        startActivityForResult(discoveryIntent, REQUEST_BLU);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode == DISCOVER_DURATION && requestCode == REQUEST_BLU) {

            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_SEND);
            intent.setType("*/*");

            File f = new File(path);
            intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));

            PackageManager pm = getPackageManager();
            List<ResolveInfo> appsList = pm.queryIntentActivities(intent, 0);

            if (appsList.size() > 0) {
                String packageName = null;
                String className = null;
                boolean found = false;

                for (ResolveInfo info : appsList) {
                    packageName = info.activityInfo.packageName;
                    if (packageName.equals("com.android.bluetooth")) {
                        className = info.activityInfo.name;
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    Toast.makeText(this, "Bluetooth has not been found",
                            Toast.LENGTH_LONG).show();
                } else {
                    intent.setClassName(packageName, className);
                    startActivity(intent);
                }
            }
        } else if (requestCode == 1001
                && resultCode == Activity.RESULT_OK) {
            Uri uriPath = data.getData();
            Log.d("", "Video URI= " + uriPath);

            path = getPath(this, uriPath);// "/mnt/sdcard/FileName.mp3"
            System.out.println("Path:  " + path);

        } else {
            Toast.makeText(this, "Bluetooth is cancelled", Toast.LENGTH_LONG)
                    .show();
        }
    }

    private boolean canAccessWriteStorage() {
        return (hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE));
    }

    private boolean canAccessReadStorage() {
        return (hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE));
    }

    private boolean canAccessReadContacts() {
        return (hasPermission(Manifest.permission.READ_CONTACTS));
    }

    private boolean canAccessWriteContacts() {
        return (hasPermission(Manifest.permission.WRITE_CONTACTS));
    }

    private boolean canAccessCamera() {
        return (hasPermission(Manifest.permission.CAMERA));
    }

    private boolean canAccessLocation() {
        return (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION));
    }

    private boolean hasPermission(String perm) {
        return (PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(this, perm));
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public static String getPath(final Context context, final Uri uri) {
        final boolean isKitKatOrAbove = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKatOrAbove && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
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
                final String[] selectionArgs = new String[]{
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context       The context.
     * @param uri           The Uri to query.
     * @param selection     (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }


    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }


    private boolean checkPermission() {
        int result = ContextCompat.checkSelfPermission(Screen6.this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (result == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            return false;
        }
    }

    private void requestPermission() {

        if (ActivityCompat.shouldShowRequestPermissionRationale(Screen6.this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Toast.makeText(Screen6.this, "Write External Storage permission allows us to create files. Please allow this permission in App Settings.", Toast.LENGTH_LONG).show();
        } else {
            ActivityCompat.requestPermissions(Screen6.this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        }
    }


    public void openHome() {
        Intent intent9 = new Intent(this, MainActivity.class);
        startActivity(intent9);
    }

}
