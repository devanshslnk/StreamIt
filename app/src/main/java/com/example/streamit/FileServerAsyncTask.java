package com.example.streamit;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.example.streamit.DeviceDetailFragment;
import com.example.streamit.LocalFileStreamingServer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class FileServerAsyncTask extends AsyncTask<Void, Void, String> {

    private Context context;
    //private TextView statusText;
    private int port;

    /**
     * @param context
     */
    public FileServerAsyncTask(Context context, int port) {
        this.context = context;
        //this.statusText = (TextView) statusText;
        this.port = port;
    }
    @Override
    protected String doInBackground(Void... params) {
        try {
            ServerSocket serverSocket = new ServerSocket(this.port);
            Log.d(MainActivity.TAG, "Server: Socket opened");
            Socket client = serverSocket.accept();
            Log.d(MainActivity.TAG, "Server: connection done");


            final File f = new File(Environment.getExternalStorageDirectory() + "/"
                    + context.getPackageName() + "/wifip2pshared-" + System.currentTimeMillis()
                    + ".mp4");

            File dirs = new File(f.getParent());
            if (!dirs.exists())
                dirs.mkdirs();
            f.createNewFile();

            Log.d(MainActivity.TAG, "server: copying files " + f.toString());
            InputStream inputstream = client.getInputStream();


            copyFile(inputstream, new FileOutputStream(f));
            Log.d(MainActivity.TAG,"reach here");
            serverSocket.close();
            return f.getAbsolutePath();
            //return null;
        } catch (IOException e) {
            Log.e(MainActivity.TAG, e.getMessage());
            return null;
        }
    }


    protected void onPostExecute(String result) {
//        if (result != null) {
//            //statusText.setText("File copied - " + result);
//            Intent intent = new Intent();
//            intent.setAction(android.content.Intent.ACTION_VIEW);
//            intent.setDataAndType(Uri.parse("file://" + result), "video/*");
//            context.startActivity(intent);
//
//        }
    }

    public static boolean copyFile(InputStream inputStream, OutputStream out) {
        byte buf[] = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.close();
            inputStream.close();
        } catch (IOException e) {
            Log.d(MainActivity.TAG, "file trans "+e.toString());
            return false;
        }
        return true;
    }
}