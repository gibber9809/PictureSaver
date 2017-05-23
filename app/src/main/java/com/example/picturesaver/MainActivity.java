package com.example.picturesaver;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import static android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_STORAGE_PERMISSION = 100;
    private static final String[] supportedFormats = {"jpg", "png", "bmp", "webp"};
    private GridView mThumbGrid = null;
    private String mCurrentFile = null;
    private thumbnailAdapter mAdapter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        mAdapter = new thumbnailAdapter(new ArrayList<String>());

        mThumbGrid =(GridView) findViewById(R.id.thumbnail_holder);
        mThumbGrid.setAdapter(mAdapter);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            loadCurrentImages();
        } else {
            requestStoragePermission();
        }

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

                mAdapter.add(mCurrentFile);
                mAdapter.notifyDataSetChanged();
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, R.string.picture_cancelled, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch(requestCode) {
            case REQUEST_STORAGE_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //takePicture(null);
                } else {
                    Toast.makeText(this, R.string.action_cancelled, Toast.LENGTH_SHORT).show();
                }
        }
    }

    private void loadCurrentImages() {
        File mediaDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        File[] mediaList = mediaDirectory.listFiles();

        for (File toLoad: mediaList) {
            String name = toLoad.getName();
            if (name.length() >= 4){
                String ext1 = name.substring(name.length() - 3);
                String ext2 = name.substring(name.length() - 4);
                for (String extension: supportedFormats) {
                    if (ext1.equals(extension) || ext2.equals(extension)) {
                        //toLoad.delete();
                        mAdapter.add(toLoad.getPath());
                    }
                }
            }
        }
        mAdapter.notifyDataSetChanged();
    }

    private File makeNewFile() throws IOException {
        String dateStamp = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_").format(new Date());
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
                    pictureFile = makeNewFile();
                    mCurrentFile = pictureFile.getPath();
                } catch (IOException e) {
                    //bad
                }
                Uri pictureURI = FileProvider.getUriForFile(this, "com.example.fileprovider", pictureFile);

                pictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, pictureURI);
                pictureIntent.setFlags(FLAG_GRANT_WRITE_URI_PERMISSION);

                startActivityForResult(pictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        } else {
            requestStoragePermission();
        }
    }

    private void requestStoragePermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQUEST_STORAGE_PERMISSION);
    }

    private class thumbnailAdapter extends BaseAdapter {
        private ArrayList<String> mFilePaths;
        private static final int SCALE_FACTOR = 200;

        public thumbnailAdapter(ArrayList<String> filePaths) {
            mFilePaths = filePaths;
        }

        public int getCount() {
            return mFilePaths.size();
        }

        public Object getItem(int position) {
            return mFilePaths.get(position);
        }

        public long getItemId(int position) {
            return mFilePaths.get(position).hashCode();
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null || convertView.getTag() != mFilePaths.get(position)) {
                View tempView = getLayoutInflater().inflate(R.layout.thumbnail, parent, false);

                //Set Bitmap for thumbnail
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = false;

                int scaleTarget =(int) getResources().getDisplayMetrics().density * SCALE_FACTOR;

                opts.inSampleSize = getInSampleSize(mFilePaths.get(position),
                        scaleTarget);
                ImageView tempImage = (ImageView) tempView.findViewById(R.id.thumbnail);
                tempImage.setImageBitmap(BitmapFactory.decodeFile(mFilePaths.get(position), opts));

                //Set text for location (for now it is just filename)
                TextView tempText = (TextView) tempView.findViewById(R.id.location);
                tempText.setText(mFilePaths.get(position));

                //set tag to avoid item duplication
                tempView.setTag(mFilePaths.get(position));

                return tempView;
            }
            return convertView;
        }

        public void add(String path) {
            mFilePaths.add(path);
        }
    }

    private int getInSampleSize(String filename, int targetScale) {
        int inSampleSize = 1;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filename,options);
        final int height = options.outHeight;
        final int width = options.outWidth;

        while (height/(inSampleSize) > targetScale && width/(inSampleSize) > targetScale) {
            inSampleSize*=2;
        }
        return inSampleSize;
    }

}
