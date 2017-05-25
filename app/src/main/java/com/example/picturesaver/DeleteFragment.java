package com.example.picturesaver;


import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;

/**
 * A simple {@link DialogFragment} subclass.
 * Allows user to decide whether or not they will delete remote
 * database files.
 */
public class DeleteFragment extends DialogFragment {
    DeleteFragmentListener mListener = null;

    public interface DeleteFragmentListener {
        void approved();
        void denied();
    }

    public DeleteFragment() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        if (context instanceof DeleteFragmentListener) {
            mListener = (DeleteFragmentListener) context;
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setTitle(R.string.delete_also_title);
        builder.setMessage(R.string.delete_extra_info);

        builder.setPositiveButton(R.string.accept_button, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                mListener.approved();
            }
        });

        builder.setNegativeButton(R.string.deny_button, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mListener.denied();
            }
        });

        return builder.create();
    }


}
