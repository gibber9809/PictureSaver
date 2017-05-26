package com.example.picturesaver;

import android.Manifest;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
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
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

public class MainActivity extends AppCompatActivity
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        LocationListener{
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_STORAGE_PERMISSION = 100;
    private static final int REQUEST_LOCATION_PERMISSION = 3524;
    private static final int REQUEST_APP_LOCATION_PERMISSION = 246;
    private static final String mNegativeLon = "W";
    private static final String mNegativeLat = "S";
    private static final String STORAGE_REFERENCE_TAG = "STORAGE_REFERENCE_TAG";
    private static final String LOCATION_ENABLED_TAG = "LOCATION_ENABLED_TAG";
    private static final String[] supportedFormats = {"jpg", "png", "bmp", "webp"};
    private static final int mLocationPrecision = 1000;
    private String mCurrentFile = null;
    private thumbnailAdapter mAdapter = null;

    private FirebaseAuth mAuth;
    private StorageReference mStoreRoot;
    private DatabaseReference mData;

    private GoogleApiClient mGoogleClient = null;
    private boolean mClientConnected = false;
    private LocationRequest mLocationRequest = null;
    private Location mCurrentLocation = null;
    private boolean mLocationEnabled = false;
    private boolean mStopAskingLocationPermission = false;
    private boolean mStartedTrackingLocation = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        mData = FirebaseDatabase.getInstance().getReference();
        if (savedInstanceState == null)
            mStoreRoot = FirebaseStorage.getInstance().getReference();

        mAdapter = new thumbnailAdapter(new ArrayList<String>());
        GridView thumbGrid =(GridView) findViewById(R.id.thumbnail_holder);
        thumbGrid.setAdapter(mAdapter);

        //Create a new GoogleApiClient if necessary
        if (mGoogleClient == null) {
            mClientConnected = false;
            mGoogleClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        //Create a new location request and set mLocationRequest to that value if necessary
        if (mLocationRequest == null) {
            createLocationRequest();
        }

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STORAGE_REFERENCE_TAG, mStoreRoot.toString());
        outState.putBoolean(LOCATION_ENABLED_TAG, mLocationEnabled);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        mLocationEnabled = savedInstanceState.getBoolean(LOCATION_ENABLED_TAG);

        final String stringStorageRef = savedInstanceState.getString(STORAGE_REFERENCE_TAG);
        if (stringStorageRef == null)
            return;
        mStoreRoot = FirebaseStorage.getInstance().getReferenceFromUrl(stringStorageRef);
        List<FileDownloadTask> activeDownloads = mStoreRoot.getActiveDownloadTasks();
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

        //Connect our Google API Client
        mGoogleClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();

        //Disconnect our Google API Client
        mGoogleClient.disconnect();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_IMAGE_CAPTURE:
                if (resultCode == RESULT_OK) {
                    Intent scanNewMedia = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    File f = new File(mCurrentFile);
                    Uri pictureURI = FileProvider.getUriForFile(this, "com.example.fileprovider", f);
                    scanNewMedia.setData(pictureURI);
                    this.sendBroadcast(scanNewMedia);

                    if (mLocationEnabled && mCurrentLocation != null) {
                        try {
                            double latNumerator = mCurrentLocation.getLatitude() * mLocationPrecision;
                            double lonNumerator = mCurrentLocation.getLongitude() * mLocationPrecision;
                            ExifInterface metaWriter = new ExifInterface(f.getPath());

                            createRationalAttribute(metaWriter, ExifInterface.TAG_GPS_LONGITUDE,
                                    lonNumerator, mNegativeLon, ExifInterface.TAG_GPS_LONGITUDE_REF);
                            createRationalAttribute(metaWriter, ExifInterface.TAG_GPS_LATITUDE,
                                    latNumerator, mNegativeLat, ExifInterface.TAG_GPS_LATITUDE_REF);

                            metaWriter.saveAttributes();
                        } catch (IOException e) {
                            //Safe to ignore
                        }
                    }

                    mAdapter.add(mCurrentFile);
                    mAdapter.notifyDataSetChanged();

                    //All just basic implementation, worry about edge cases later
                    mStoreRoot.child("images/" + pictureURI.getLastPathSegment()).putFile(pictureURI);

                    String key = mData.push().getKey();
                    mData.child(key).setValue(pictureURI.getLastPathSegment());

                } else if (resultCode == RESULT_CANCELED) {
                    File toDelete = new File(mCurrentFile);
                    while (toDelete.exists()) {
                        toDelete.delete();
                    }
                    mAdapter.remove(mCurrentFile);
                    mAdapter.notifyDataSetChanged();
                    Toast.makeText(this, R.string.picture_cancelled, Toast.LENGTH_SHORT).show();
                }
                break;
            case REQUEST_LOCATION_PERMISSION:
                if (resultCode == RESULT_OK) {
                    mLocationEnabled = true;
                    takePicture();
                } else if (resultCode == RESULT_CANCELED) {
                    mLocationEnabled = false;
                    mStopAskingLocationPermission = true;
                    takePicture();
                }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch(requestCode) {
            case REQUEST_STORAGE_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mAdapter.clear();
                    loadCurrentImages();
                } else {
                    Toast.makeText(this, R.string.action_cancelled, Toast.LENGTH_SHORT).show();
                }
                break;
            case REQUEST_APP_LOCATION_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkPermissionsThenTakePicture(null);
                } else {
                    mStopAskingLocationPermission = true;
                    checkPermissionsThenTakePicture(null);
                }
                break;
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        mClientConnected = true;

        //If we had permissions before we probably still have them now, might as well
        //start tracking location if we can
        if (mLocationEnabled) {
            try {
                LocationServices.FusedLocationApi.requestLocationUpdates(
                        mGoogleClient, mLocationRequest, this);
                mStartedTrackingLocation = true;
            } catch(SecurityException e) {
                //ignore
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = location;
    }

    @Override
    public void onConnectionSuspended(int id) {
        mClientConnected = false;
        //Why does this happen to me
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        mLocationEnabled = false;
        mClientConnected = false;
        //I know I've made my mistakes
    }

    private void createRationalAttribute(ExifInterface writer, String regularTag, double numerator, String negative, String negativeTag) {
        if (numerator < 0) {
            numerator *= -1;
            writer.setAttribute(negativeTag, negative);
        }
        String top = Double.toString(numerator);
        String newNumerator = top.substring(0, top.indexOf("."));
        writer.setAttribute(regularTag, getString(R.string.rational_format, newNumerator, Integer.toString(mLocationPrecision)));
    }

    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setFastestInterval(20_000);
        mLocationRequest.setInterval(30_000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
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
                        FileDownloadTask task = mStoreRoot.child("images/" + remoteFilename).getFile(downloadURI);

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
                if (mStoreRoot.getActiveDownloadTasks().size() == 0)
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

    /**
     * Checks permissions before allowing the user to take a picture
     *
     * Makes sure that Write Permissions have been granted, and attempts to secure
     * Location permissions. The picture can still be taken if Write permissions have been granted,
     * but not location permissions
     *
     */
    public void checkPermissionsThenTakePicture(View view) {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {

            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    mStopAskingLocationPermission) {

                if (!mClientConnected) {
                    mLocationEnabled = false;
                    Toast.makeText(this, R.string.location_not_connected, Toast.LENGTH_SHORT).show();
                    takePicture();
                    return;
                }

                LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                        .addLocationRequest(mLocationRequest);
                PendingResult<LocationSettingsResult> result =
                        LocationServices.SettingsApi.checkLocationSettings(mGoogleClient,
                                builder.build());

                result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
                    @Override
                    public void onResult(@NonNull LocationSettingsResult result) {
                        final Status status = result.getStatus();

                        switch (status.getStatusCode()) {
                            case LocationSettingsStatusCodes.SUCCESS:
                                mLocationEnabled = true;
                                takePicture();
                                break;
                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                if (!mStopAskingLocationPermission) {
                                    try {
                                        status.startResolutionForResult(MainActivity.this, REQUEST_LOCATION_PERMISSION);
                                    } catch (IntentSender.SendIntentException e) {
                                        //Ignore this error
                                    }
                                } else {
                                    takePicture();
                                }
                                break;
                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                mLocationEnabled = false;
                                takePicture();
                                break;
                        }
                    }
                });
            } else {
                requestAppLocationPermission();
            }

        } else {
            requestStoragePermission();
        }

    }

    public void takePicture() {
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

            if (mLocationEnabled && mClientConnected && !mStartedTrackingLocation) {
                try {
                    LocationServices.FusedLocationApi.requestLocationUpdates(
                            mGoogleClient, mLocationRequest, this);
                    mStartedTrackingLocation = true;
                } catch(SecurityException e) {
                    //This will never be called, it is actually impossible
                }
            }

            startActivityForResult(pictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    private void requestStoragePermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQUEST_STORAGE_PERMISSION);
    }

    private void requestAppLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                REQUEST_APP_LOCATION_PERMISSION);
    }


    /**
     * Inner class to be the adapter for the Gridview present in the main activity
     */
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
                double[] coords = new double[2];
                if (dataReader != null && getLatLong(
                        coords,
                        dataReader.getAttribute(ExifInterface.TAG_GPS_LATITUDE),
                        dataReader.getAttribute(ExifInterface.TAG_GPS_LONGITUDE),
                        dataReader.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF),
                        dataReader.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF))) {

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

        public void remove(String path) {
            mFilePaths.remove(path);
        }

        public void clear() {
            mFilePaths.clear();
        }

        private boolean getLatLong(double[] coords, String lat, String lon, String nLat, String nLon) {
            if (lat == null || lon == null)
                return false;

            String[] split;
            try {
                split = lat.split("/");
                coords[0] = Double.valueOf(split[0]) / Double.valueOf(split[1]);
                if (nLat != null && nLat.equals(mNegativeLat))
                    coords[0] *= -1;

                split = lon.split("/");
                coords[1] = Double.valueOf(split[0]) / Double.valueOf(split[1]);
                if (nLon != null && nLon.equals(mNegativeLon))
                    coords[1] *= -1;

                return true;
            } catch (ArrayIndexOutOfBoundsException e) {
                return false;
            }
        }

        private View.OnClickListener generateNewClickListener(final String filePath) {
            return new View.OnClickListener() {
                public void onClick(View v) {
                    if (ContextCompat.checkSelfPermission(
                            MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                            PackageManager.PERMISSION_GRANTED) {

                        Intent viewPhoto = new Intent(MainActivity.this, PictureViewer.class);
                        viewPhoto.putExtra(PictureViewer.FILE_PATH_KEY, filePath);
                        startActivity(viewPhoto);
                    }
                    else {
                        requestStoragePermission();
                    }
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
