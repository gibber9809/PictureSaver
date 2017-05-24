package com.example.picturesaver;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.Toast;

public class PictureViewer extends AppCompatActivity {
    public static final String FILE_PATH_KEY = "FILE_PATH_KEY";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String filePath = intent.getStringExtra(FILE_PATH_KEY);

        setContentView(R.layout.activity_picture_viewer);

        //Set the bitmap for the image we will view
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = false;
        opts.inSampleSize = MainActivity.getInSampleSize(
                filePath,
                getResources().getDisplayMetrics().heightPixels);

        ImageView picture =(ImageView) findViewById(R.id.picture);
        picture.setImageBitmap(BitmapFactory.decodeFile(filePath, opts));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.delete_menu, menu);

        return super.onCreateOptionsMenu(menu);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.delete_button:
                //do stuff
                return(true);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

}
