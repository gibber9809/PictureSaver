package com.example.picturesaver;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;


/**
 * Fragment to display photo thumbnails, and communicate with activity when clicked
 */
public class ThumbnailFragment extends Fragment {
    private static String PATH_ARGUMENT = "com.example.picturesaver.PATH_ARGUMENT";
    private String mPath = null;
    private ImageView mThumbnail = null;
    private thumbnailLoader mLoader = null;

    public ThumbnailFragment() {
        // Required empty public constructor
    }

    public static ThumbnailFragment newInstance(String path) {
        ThumbnailFragment thumbnail = new ThumbnailFragment();
        Bundle arguments = new Bundle();
        arguments.putString(PATH_ARGUMENT, path);
        thumbnail.setArguments(arguments);
        return thumbnail;
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Bundle args = getArguments();
        if (args != null) {
            mPath = args.getString(PATH_ARGUMENT);
        }

        // Inflate the layout for this fragment
        View fragmentView = inflater.inflate(R.layout.thumbnail, container, false);
        mThumbnail =(ImageView) fragmentView.findViewById(R.id.thumbnail);

        setRetainInstance(true);

        if (mPath != null && mLoader == null) {
            mLoader = new thumbnailLoader(mThumbnail.getWidth(), mThumbnail.getHeight());
            mLoader.execute(mPath);
        }

        return fragmentView;
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mLoader != null) {
            mLoader.cancel(true);
        }
    }

    private class thumbnailLoader extends AsyncTask<String, Void, Bitmap> {
        private int mTargetWidth, mTargetHeight;
        public thumbnailLoader(int width, int height) {
            mTargetWidth = width;
            mTargetHeight = height;
        }

        protected Bitmap doInBackground(String... path) {
            BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
            decodeOptions.inJustDecodeBounds = false;
            decodeOptions.inSampleSize = getInSampleSize(path[0]);

            return BitmapFactory.decodeFile(path[0], decodeOptions);

        }

        protected void onPostExecute(Bitmap thumbnailBitmap) {
            if (!isCancelled()) {
                mThumbnail.setImageBitmap(thumbnailBitmap);
            }
            mLoader = null;
        }

        private int getInSampleSize(String filename) {
            int inSampleSize = 1;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(filename,options);
            final int height = options.outHeight;
            final int width = options.outWidth;

            while (height/(2*inSampleSize) > mTargetHeight && width/(2*inSampleSize) > mTargetWidth) {
                inSampleSize*=2;
            }
            return inSampleSize;
        }


    }

}
