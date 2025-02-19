/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (C) 2011 - 2019 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.trainingtracker.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.atrainingtracker.R;
import com.atrainingtracker.trainingtracker.TrainingApplication;
import com.atrainingtracker.trainingtracker.interfaces.StartOrResumeInterface;

/**
 * Created by rainer on 05.01.17.
 */

public class StartOrResumeDialog extends DialogFragment {
    public static final String TAG = StartOrResumeDialog.class.getName();
    private static final boolean DEBUG = TrainingApplication.DEBUG && false;

    private StartOrResumeInterface mStartOrResumeInterface;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the StartOrResumeInterface so we can send events to the host
            if (context instanceof StartOrResumeInterface) {
                mStartOrResumeInterface = (StartOrResumeInterface) context;
            } else {
                throw new ClassCastException(context.toString() + " must implement StartOrResumeInterface");
            }
        } catch (ClassCastException e) {
            // Handle the exception if the context does not implement the required interface
            throw new ClassCastException(context.toString() + " must implement StartOrResumeInterface");
        }
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getContext());

        // Set the message for the dialog
        alertDialogBuilder.setMessage(R.string.start_or_resume_dialog_message);

        // Set up the positive button and its click listener
        alertDialogBuilder.setPositiveButton(R.string.start_new_workout, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                try {
                    if (mStartOrResumeInterface != null) {
                        mStartOrResumeInterface.chooseStart();
                    } else {
                        Log.e("DialogError", "mStartOrResumeInterface is null");
                    }
                } catch (Exception e) {
                    e.printStackTrace(); // Log the exception
                    Log.e("DialogError", "Error while choosing start workout", e);
                    Toast.makeText(getContext(), "Error starting workout", Toast.LENGTH_SHORT).show();
                } finally {
                    dialog.cancel(); // Ensure dialog is canceled
                }
            }
        });

        // Set up the negative button and its click listener
        alertDialogBuilder.setNegativeButton(R.string.resume_workout, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                try {
                    if (mStartOrResumeInterface != null) {
                        mStartOrResumeInterface.chooseResume();
                    } else {
                        Log.e("DialogError", "mStartOrResumeInterface is null");
                    }
                } catch (Exception e) {
                    e.printStackTrace(); // Log the exception
                    Log.e("DialogError", "Error while choosing resume workout", e);
                    Toast.makeText(getContext(), "Error resuming workout", Toast.LENGTH_SHORT).show();
                } finally {
                    dialog.cancel(); // Ensure dialog is canceled
                }
            }
        });

        // Create and return the dialog
        AlertDialog dialog = alertDialogBuilder.create();
        // Optionally, you can add more dialog configurations here

        return dialog;
    }
}
