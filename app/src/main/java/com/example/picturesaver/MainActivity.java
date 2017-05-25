package com.example.picturesaver;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_STORAGE_PERMISSION = 100;
    private static final String STORAGE_REFERENCE_TAG = "STORAGE_REFERENCE_TAG";
    private static final String[] supportedFormats = {"jpg", "png", "bmp", "webp"};
    private String mCurrentFile = null;
    private thumbnailAdapter mAdapter = null;

    private FirebaseAuth mAuth;
    private StorageReference mRoot;
    private DatabaseReference mData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        mRoot = FirebaseStorage.getInstance().getReference();
        mData = FirebaseDatabase.getInstance().getReference();

        mAdapter = new thumbnailAdapter(new ArrayList<String>());
        GridView thumbGrid =(GridView) findViewById(R.id.thumbnail_holder);
        thumbGrid.setAdapter(mAdapter);

    }

    @Override
    protected void onSaveInstanceState(Bundle outstate) {
        outstate.putString(STORAGE_REFERENCE_TAG, mRoot.toString());

        super.onSaveInstanceState(outstate);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        final String stringStorageRef = savedInstanceState.getString(STORAGE_REFERENCE_TAG);
        if (stringStorageRef == null)
            return;
        mRoot = FirebaseStorage.getInstance().getReferenceFromUrl(stringStorageRef);
        List<FileDownloadTask> activeDownloads = mRoot.getActiveDownloadTasks();
        for (FileDownloadTask task: activeDownloads) {
            task.addOnSuccessListener(this, generateNewDownloadSuccessListener());
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.restore_photos, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.restore_button:
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    mData.addListenerForSingleValueEvent(generateFileRestoreListener());
                } else {
                    requestStoragePermission();
                }

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            mAdapter.clear();
            loadCurrentImages();
        } else {
            requestStoragePermission();
        }

        if (mAuth.getCurrentUser() == null) {
            mAuth.signInAnonymously().addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                @Override
                public void onComplete(@NonNull Task<AuthResult> task) {
                    if (!task.isSuccessful()) {
                        Toast.makeText(MainActivity.this, R.string.anon_auth_failed,
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE) {
            if (resultCode == RESULT_OK) {
                Intent scanNewMedia = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                File f = new File(mCurrentFile);
                Uri pictureURI = FileProvider.getUriForFile(this, "com.example.fileprovider", f);
                scanNewMedia.setData(pictureURI);
                this.sendBroadcast(scanNewMedia);

                //temporary until really using location
                try {
                    ExifInterface metaWriter = new ExifInterface(f.getPath());
                    metaWriter.setAttribute(ExifInterface.TAG_GPS_LONGITUDE,"1/1");
                    metaWriter.setAttribute(ExifInterface.TAG_GPS_LATITUDE, "127/1");
                    metaWriter.saveAttributes();
                }catch(IOException e) {
                    //Safe to ignore
                }

                mAdapter.add(mCurrentFile);
                mAdapter.notifyDataSetChanged();

                //All just basic implementation, worry about edge cases later
                mRoot.child("images/" + pictureURI.getLastPathSegment()).putFile(pictureURI);

                String key = mData.push().getKey();
                mData.child(key).setValue(pictureURI.getLastPathSegment());

            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, R.string.picture_cancelled, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch(requestCode) {
            case REQUEST_STORAGE_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //takePicture(null);
                } else {
                    Toast.makeText(this, R.string.action_cancelled, Toast.LENGTH_SHORT).show();
                }
        }
    }

    private ValueEventListener generateFileRestoreListener() {
        return new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                File mediaDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                File[] localFiles = mediaDirectory.listFiles(getFormatFilter());
                boolean changesNecessary = false;
                for (DataSnapshot filename: dataSnapshot.getChildren()) {
                    String remoteFilename = filename.getValue().toString();
                    boolean locallyAvailable = false;
                    for (File localFile:localFiles) {
                        if (remoteFilename.equals(localFile.getName())) {
                            locallyAvailable = true;
                            break;
                        }
                    }
                    if (!locallyAvailable) {
                        changesNecessary = true;
                        File f = new File(mediaDirectory.getPath(),remoteFilename);

                        if (!f.exists()) {
                            try {
                                f.createNewFile();
                            } catch (IOException e) {
                                continue;
                            }
                        }

                        Uri downloadURI = Uri.fromFile(f);
                        FileDownloadTask task = mRoot.child("images/" + remoteFilename).getFile(downloadURI);

                        //set a listener for task
                        task.addOnSuccessListener(MainActivity.this, generateNewDownloadSuccessListener());
                    }
                }

                //Inform the user about downloads starting, or already having local copies of everything
                if (changesNecessary) {
                    Toast.makeText(MainActivity.this, R.string.downloads_started, Toast.LENGTH_SHORT)
                            .show();
                } else {
                    Toast.makeText(MainActivity.this, R.string.downloads_unnecessary, Toast.LENGTH_SHORT)
                            .show();
                }

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Toast.makeText(MainActivity.this, R.string.restore_failed, Toast.LENGTH_SHORT).show();
            }
        };
    }

    private OnSuccessListener<FileDownloadTask.TaskSnapshot> generateNewDownloadSuccessListener() {
        return new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                String filename = taskSnapshot.getStorage().getName();
                File toAdd = new File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                        filename);

                mAdapter.add(toAdd.getPath());
                mAdapter.notifyDataSetChanged();

                //Let the user know that this (the last download) has finished
                if (mRoot.getActiveDownloadTasks().size() == 0)
                    Toast.makeText(MainActivity.this, R.string.downloads_finished, Toast.LENGTH_LONG)
                            .show();
            }
        };
    }

    private void loadCurrentImages() {
        File mediaDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        File[] mediaList = mediaDirectory.listFiles(getFormatFilter());

        for (File toLoad: mediaList) {
            mAdapter.add(toLoad.getPath());
        }

        mAdapter.notifyDataSetChanged();
    }

    /**
     * Written like it is because lastIndexOf('.'); was not working
     * @return FileFilter to sort out supported image formats
     */
    private FileFilter getFormatFilter() {
        return new FileFilter() {
            public boolean accept(File f) {
                if (f.isDirectory()) {
                    return false;
                }
                String name = f.getName();
                if (name.length() >= 4) {
                    String ext1 = name.substring(name.length() - 3);
                    String ext2 = name.substring(name.length() - 4);
                    for (String extension: supportedFormats) {
                        if (ext1.equals(extension) || (ext2.equals(extension)))
                            return true;
                    }
                }
                return false;
            }
        };
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

    class thumbnailAdapter extends BaseAdapter {
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
                Bitmap imageBitmap;
                ExifInterface dataReader;
                try {
                    dataReader = new ExifInterface(mFilePaths.get(position));
                } catch(IOException e) {
                    dataReader = null;
                }

                if (dataReader != null && dataReader.hasThumbnail()) {
                    byte[] image = dataReader.getThumbnail();
                    imageBitmap = BitmapFactory.decodeByteArray(image, 0, image.length);
                }else {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = false;

                    int scaleTarget = (int) getResources().getDisplayMetrics().density * SCALE_FACTOR;

                    opts.inSampleSize = getInSampleSize(mFilePaths.get(position),
                            scaleTarget);

                    imageBitmap = BitmapFactory.decodeFile(mFilePaths.get(position), opts);
                }
                ImageView tempImage = (ImageView) tempView.findViewById(R.id.thumbnail);
                tempImage.setImageBitmap(imageBitmap);

                //Set text for location
                float[] coords = new float[2];
                if (dataReader != null &&
                        getLatLong(
                        coords,
                        dataReader.getAttribute(ExifInterface.TAG_GPS_LATITUDE),
                        dataReader.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))) {

                    TextView tempText = (TextView) tempView.findViewById(R.id.location);
                    tempText.setText(getString(R.string.gps_format,
                            coords[0],
                            coords[1]));
                }

                tempView.setOnClickListener(generateNewClickListener(mFilePaths.get(position)));

                //set tag to avoid item duplication
                tempView.setTag(mFilePaths.get(position));

                return tempView;
            }
            return convertView;
        }

        public void add(String path) {
            mFilePaths.add(path);
        }

        public void clear() {
            mFilePaths.clear();
        }

        //TODO Remove once using actual GPS data
        private boolean getLatLong(float[] coords, String lat, String lon) {
            if (lat == null || lon == null)
                return false;
            String[] split;

            try {
                split = lat.split("/");
                coords[0] = Float.valueOf(split[0]) / Float.valueOf(split[1]);

                split = lon.split("/");
                coords[1] = Float.valueOf(split[0]) / Float.valueOf(split[1]);
                return true;
            } catch (ArrayIndexOutOfBoundsException e) {
                return false;
            }
        }

        private View.OnClickListener generateNewClickListener(final String filePath) {
            return new View.OnClickListener() {
                public void onClick(View v) {
                    Intent viewPhoto = new Intent(MainActivity.this, PictureViewer.class);
                    viewPhoto.putExtra(PictureViewer.FILE_PATH_KEY, filePath);
                    startActivity(viewPhoto);
                }
            };
        }
    }

    static int getInSampleSize(String filename, int targetScale) {
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
