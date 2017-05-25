package com.example.picturesaver;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.util.Map;

public class PictureViewer extends AppCompatActivity
        implements DeleteFragment.DeleteFragmentListener{
    public static final String FILE_PATH_KEY = "FILE_PATH_KEY";
    private String mFilePath;
    private String mFilename;
    private StorageReference mStore;
    private DatabaseReference mData;
    private String mDatabaseKey = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        mFilePath = intent.getStringExtra(FILE_PATH_KEY);

        //Get referencees to database in case of deletion
        mStore = FirebaseStorage.getInstance().getReference();
        mData = FirebaseDatabase.getInstance().getReference();

        mData.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                File f = new File(mFilePath);

                for (DataSnapshot file: dataSnapshot.getChildren()) {
                    if (file.getValue().equals(f.getName())) {
                        mDatabaseKey = file.getKey();
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                //Probably offline
            }
        });

        setContentView(R.layout.activity_picture_viewer);

        //Set the bitmap for the image we will view
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = false;
        opts.inSampleSize = MainActivity.getInSampleSize(
                mFilePath,
                getResources().getDisplayMetrics().heightPixels);

        ImageView picture =(ImageView) findViewById(R.id.picture);
        picture.setImageBitmap(BitmapFactory.decodeFile(mFilePath, opts));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.delete_menu, menu);

        return super.onCreateOptionsMenu(menu);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.delete_button:
                File f = new File(mFilePath);

                if (mDatabaseKey != null) {
                    mFilename = f.getName();
                    DeleteFragment deleteDialog = new DeleteFragment();
                    deleteDialog.show(getSupportFragmentManager(), null);
                    if (!f.delete())
                        f.delete();
                } else {
                    if (!f.delete())
                        f.delete();
                    this.finish();
                }

                return(true);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void approved() {
        mStore.child("images/" + mFilename).delete().addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void ignore) {
                mData.child(mDatabaseKey).removeValue();
            }
        });
        this.finish();
    }

    public void denied() {
        this.finish();
    }

}
