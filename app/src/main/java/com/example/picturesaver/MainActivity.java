package com.example.picturesaver;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
import static android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_STORAGE_PERMISSION = 100;
    private String mCurrentFile = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE) {
            if (resultCode == RESULT_OK) {
                Intent scanNewMedia = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                File f = new File(mCurrentFile);
                Uri pictureURI = Uri.fromFile(f);
                scanNewMedia.setData(pictureURI);
                this.sendBroadcast(scanNewMedia);

            } else if (resultCode == RESULT_CANCELED) {
                //File unused = new File(mCurrentFile);
                //unused.delete();
                Toast.makeText(this, R.string.picture_cancelled, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch(requestCode) {
            case REQUEST_STORAGE_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    takePicture(null);
                } else {
                    this.finish();
                }
                return;
        }
    }

    private File getNewFile() throws IOException {
        String dateStamp = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date());
        String filename = "IMAGE_" + dateStamp;
        File storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);

        return File.createTempFile(filename, ".jpg", storageDirectory);
    }

    public void takePicture(View view) {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            Intent pictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (pictureIntent.resolveActivity(getPackageManager()) != null) {
                File pictureFile = null;
                try {
                    pictureFile = getNewFile();
                    mCurrentFile = pictureFile.getAbsolutePath();
                } catch (IOException e) {
                    //bad
                }
                Uri pictureURI = FileProvider.getUriForFile(this, "com.example.fileprovider", pictureFile);

                pictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, pictureURI);
                pictureIntent.setFlags(FLAG_GRANT_WRITE_URI_PERMISSION);

                startActivityForResult(pictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_STORAGE_PERMISSION);
        }
    }


}
