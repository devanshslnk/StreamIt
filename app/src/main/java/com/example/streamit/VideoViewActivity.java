package com.example.streamit;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Bundle;
import android.util.Log;
import android.widget.MediaController;
import android.widget.VideoView;

public class VideoViewActivity extends Activity {

    // Declare variables
    ProgressDialog pDialog;
    VideoView videoview;

    // Insert your Video URL
    public String VideoURL;
    @Override
    protected void onStop() {
        super.onStop();
        Intent returnIntent = new Intent();
        setResult(22, returnIntent);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Get the layout from video_main.xml
        setContentView(R.layout.videoview_main);
        // Find your VideoView in your video_main.xml layout
        videoview = (VideoView) findViewById(R.id.VideoView);
//        videoview.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
//
//            @Override
//            public void onCompletion(MediaPlayer mp) {
//                Intent returnIntent = new Intent();
//                setResult(21, returnIntent);
//            }
//        });
        // Create a progressbar
        pDialog = new ProgressDialog(VideoViewActivity.this);
        // Set progressbar title
        pDialog.setTitle("Streaming");
        // Set progressbar message
        pDialog.setMessage("Buffering...");
        pDialog.setIndeterminate(false);
        pDialog.setCancelable(false);
        // Show progressbar
        pDialog.show();

        try {
            // Start the MediaController
            MediaController mediacontroller = new MediaController(
                    VideoViewActivity.this);
            mediacontroller.setAnchorView(videoview);
            // Get the URL from String VideoURL
            //Uri video = Uri.parse(VideoURL);
            videoview.setMediaController(mediacontroller);
            //videoview.setVideoURI(video);

            VideoURL = getIntent().getData().toString();
            videoview.setVideoPath(VideoURL);
            videoview.setOnErrorListener(new MediaPlayer.OnErrorListener(){
                @Override
                public boolean onError(MediaPlayer mp,int what, int extra){
                    Log.e(MainActivity.TAG,"something error happened");
                    return false;
                }
            });
        } catch (Exception e) {
            Log.e("Error", e.getMessage());
            e.printStackTrace();
        }

        videoview.requestFocus();
        videoview.setOnPreparedListener(new OnPreparedListener() {
            // Close the progress bar and play the video
            public void onPrepared(MediaPlayer mp) {
                pDialog.dismiss();
                videoview.start();
            }
        });

    }

}