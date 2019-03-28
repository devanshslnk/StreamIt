package com.example.streamit;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.example.streamit.DeviceListFragment.DeviceActionListener;
import com.example.streamit.LocalFileStreamingServer;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Scanner;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.streamit.LocalFileStreamingServer;

/**
 * A fragment that manages a particular peer and allows interaction with device
 * i.e. setting up network connection and transferring data.
 */
public class DeviceDetailFragment extends Fragment implements ConnectionInfoListener {

    protected static final int CHOOSE_FILE_RESULT_CODE = 20;
    protected static final int PLAY_VIDEO_RESULT_CODE = 21;
    private final int MSG_IP = 0;
    private final int ACTIVE = 1;
    private final int MSG_PORT = 2;
    private final int MSG_BYE = 3;
    private final int WARNING = 4;
    private View mContentView = null;
    private WifiP2pDevice device;
    private WifiP2pInfo info;
    private String myIP = null;
    private String currentplayingIP = null;
    private HashSet<String> offerIP = new HashSet<String>();
    private ProgressDialog progressDialog = null;
    private LocalFileStreamingServer mServer = null;
    private Controlpath controlpath = null;
    private int listener = 0 ;
    private String server_file_uri;
    private DataReceiver dataReceiver;
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState) {

        mContentView = inflater.inflate(R.layout.device_detail, null);
        mContentView.findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                config.wps.setup = WpsInfo.PBC;
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                progressDialog = ProgressDialog.show(getActivity(), "Press back to cancel",
                        "Connecting to :" + device.deviceAddress, true, true
                );

                ((DeviceActionListener) getActivity()).connect(config);
            }
        });
        mContentView.findViewById(R.id.btn_disconnect).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
//                        if (mServer != null) {
//                            Log.d(MainActivity.TAG, "HTTP Server stopped without being declared");
//                            handle.obtainMessage(WARNING,"")
//                            mServer.stop();
//                        }
//                        Log.d(MainActivity.TAG, "HTTP Server Terminated");
//                        if(controlpath!=null) {
//                            controlpath.stop();
//                        }
                        ((DeviceActionListener) getActivity()).disconnect();
                        resetViews();
                        Log.d(MainActivity.TAG, "Control Path Server Terminated");
                    }
                });

        mContentView.findViewById(R.id.btn_start_client).setOnClickListener(
                new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        // Allow user to pick an image from Gallery or other
                        // registered apps
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("video/*");
                        startActivityForResult(intent, CHOOSE_FILE_RESULT_CODE);
                    }
                });
        mContentView.findViewById(R.id.stop_server).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        controlpath.stop();
                        if (mServer != null) {
                            Log.d(MainActivity.TAG, "HTTP Server stopped without being declared");
                            mServer.stop();
                            mServer = null;
                        }
                        mContentView.findViewById(R.id.stop_server).setVisibility(View.GONE);
                        ((TextView) mContentView.findViewById(R.id.btn_start_client)).setVisibility(View.VISIBLE);
                        ((TextView) mContentView.findViewById(R.id.status_text)).setText("");
                        listener = 0;
                    }
                }
        );

        return mContentView;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(MainActivity.TAG, "requestCode is " + Integer.toString(requestCode) + ";resultCode is" + Integer.toString(resultCode));
        switch (requestCode) {
            case CHOOSE_FILE_RESULT_CODE:
                if(resultCode== Activity.RESULT_OK) {
                    Log.d(MainActivity.TAG, "File chosen with result code = " + CHOOSE_FILE_RESULT_CODE);
                    // User has picked an image.
                    listener = 0;
                    Uri uri = data.getData();
                    server_file_uri = uri.toString();
                    String server_file_path = getRealPathFromURI(uri);
                    Log.d(MainActivity.TAG, "Intent(DeviceDetailFragment)----------- " + server_file_path);

                    // Initiating and start LocalFileStreamingServer
                    mServer = new LocalFileStreamingServer(new File(server_file_path), myIP, controlpath);
                    if (null != mServer && !mServer.isRunning())
                        mServer.start();
                    mContentView.findViewById(R.id.stop_server).setVisibility(View.VISIBLE);
                    mContentView.findViewById(R.id.btn_start_client).setVisibility(View.GONE);
                }
                break;
            case PLAY_VIDEO_RESULT_CODE:
                if(resultCode==Activity.RESULT_CANCELED) {
                    Log.d(MainActivity.TAG, "Video play terminated with result code = " + Integer.toString(requestCode));
                    controlpath.sendGoodBye();
                }
                break;
            default:
                Log.d(MainActivity.TAG, "unknown result code=" + resultCode);
        }
    }

    private String getRealPathFromURI(Uri contentUri) {
        String[] proj = { MediaStore.Images.Media.DATA };
        CursorLoader loader = new CursorLoader(getActivity(), contentUri, proj, null, null, null);
        Cursor cursor = loader.loadInBackground();
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        String result = cursor.getString(column_index);
        cursor.close();
        return result;
    }
    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        //if(info==null)
        this.info = info;
        //else{
        //    this.info=null;
        //}
        this.getView().setVisibility(View.VISIBLE);
        // The owner IP is now known.
        TextView view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText(getResources().getString(R.string.group_owner_text)
                + ((info.isGroupOwner == true) ? getResources().getString(R.string.yes)
                : getResources().getString(R.string.no)));

        // InetAddress from WifiP2pInfo struct.
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText("Group Owner IP - " + info.groupOwnerAddress.getHostAddress());

        // After the group negotiation, we assign the group owner as the file
        // server. The file server is single threaded, single connection server
        // socket.

        if (info.groupFormed  ) {
            if(controlpath==null) {
                controlpath = new Controlpath(info.isGroupOwner, info.groupOwnerAddress.getHostAddress());
                controlpath.start();
            }
            else{
                Log.d(MainActivity.TAG," previously declared one");
            }
        }
        if (mServer == null){
            mContentView.findViewById(R.id.btn_start_client).setVisibility(View.VISIBLE);
        }
        //hide the connect button
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.GONE);
    }

    /**
     * Updates the UI with device data
     *
     * @param device the device to be displayed
     */
    public void showDetails(WifiP2pDevice device) {
        this.device = device;
        this.getView().setVisibility(View.VISIBLE);
        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        view.setText(device.deviceAddress);
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText(device.toString());

    }

    /**
     * Clears the UI fields after a disconnect or direct mode disable operation.
     */
    public void resetViews() {
        mContentView.findViewById(R.id.btn_connect).setVisibility(View.VISIBLE);
        mContentView.findViewById(R.id.stop_server).setVisibility(View.GONE);
        if(controlpath==null)
            Log.d(MainActivity.TAG,"no valid control path");
        else{
            Log.d(MainActivity.TAG, "There is a valid control path");
            if(myIP!=null&&myIP.equals("192.168.49.1"))
            {
                handle.obtainMessage(WARNING,"Connection with other peers has failed").sendToTarget();
            }
            else if(mServer ==null){
                handle.obtainMessage(WARNING,"Connection with other peers has failed").sendToTarget();
            }
            else if(mServer!=null){
                mServer.stop();
                handle.obtainMessage(WARNING,"Connection with other peers has failed").sendToTarget();
            }
            controlpath.stop();
        }
        resetdata();
        TextView view = (TextView) mContentView.findViewById(R.id.device_address);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.device_info);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.group_owner);
        view.setText(R.string.empty);
        view = (TextView) mContentView.findViewById(R.id.status_text);
        view.setText(R.string.empty);

        mContentView.findViewById(R.id.btn_start_client).setVisibility(View.GONE);
        this.getView().setVisibility(View.GONE);
    }

    public void resetdata(){
        Log.d(MainActivity.TAG, "myIP is exist? " + this.myIP);
        String myIP = null;
        String currentplayingIP = null;
        info = null;
        mServer = null;
        listener = 0 ;
        server_file_uri = null;
    }

    /**
     * A simple server socket that accepts connection and writes some data on
     * the stream.
     */
    public class StreamingAsyncTask extends AsyncTask<Void, Void, String> {

        private Context context;
        private TextView statusText;
        private BufferedReader peerReader;

        /**
         * @param context
         * @param statusText
         */
        public StreamingAsyncTask(Context context, View statusText, BufferedReader peerReader) {
            this.context = context;
            this.statusText = (TextView) statusText;
            this.peerReader = peerReader;
        }

        @Override
        protected String doInBackground(Void... params) {

            String url = null;

            try {
                //readLine will block until input is available
                url = peerReader.readLine();
                Log.d(MainActivity.TAG, "HTTP Server IP Address: " + url);

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse(url), "video/*");
                startActivity(intent);


            }
            catch (IOException e)
            {
                Log.e(MainActivity.TAG, e.getMessage());
            }
            return url;
        }

        @Override
        protected void onPreExecute() {
            statusText.setText("Opening a listening socket");
        }

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
            Log.d(MainActivity.TAG, e.toString());
            return false;
        }
        return true;
    }

    /*
     *  receive info from listening thread or controlthread and interact with UI.
     */
    private Handler handle = new Handler(){
        @Override
        public void handleMessage(Message msg){
            switch(msg.what){
                case MSG_IP:
                    //update my IP in device Detail Fragment
                    myIP = (String)msg.obj;
                    break;

                case ACTIVE:
                    if(currentplayingIP==null) {
                        final String uri = (String) msg.obj;
                        //Use regular expression to match IP address
                        Pattern pattern = Pattern.compile("(http://|https://){1}((\\d{1,3}\\.){3}\\d{1,3})(:\\d*/)(.*)");
                        Matcher matcher = pattern.matcher(uri);
                        final String receivedIP;
                        final String receivedfilename;

                        if (matcher.find()) {
                            receivedIP = matcher.group(2);
                            Log.d(MainActivity.TAG,receivedIP);
                            currentplayingIP = receivedIP;
                            receivedfilename = matcher.group(5);
                            Log.d(MainActivity.TAG, receivedIP);
                            AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity());
                            dialog.setTitle(receivedfilename + " is sharing video");
                            CharSequence choice_list[] = {"Play", "Download"};
                            final ArrayList mSelectedItems = new ArrayList();
                            dialog.setMultiChoiceItems(choice_list, null,
                                    new DialogInterface.OnMultiChoiceClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                                            if (isChecked) {
                                                // If the user checked the item, add it to the selected items
                                                mSelectedItems.add(which);
                                                Log.d(MainActivity.TAG, "mSelectedItems.add: " + which);

                                            } else if (mSelectedItems.contains(which)) {
                                                // Else, if the item is already in the array, remove it
                                                mSelectedItems.remove(Integer.valueOf(which));
                                            }
                                        }
                                    });
                            dialog.setCancelable(false);
                            dialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (mSelectedItems.contains(0)) {
                                        Log.d(MainActivity.TAG, "Play video");
                                        Intent intent = new Intent(getActivity().getApplicationContext(), VideoViewActivity.class);
                                        intent.setDataAndType(Uri.parse(uri), "video/*");
                                        startActivityForResult(intent, PLAY_VIDEO_RESULT_CODE);
                                    }
                                    if (mSelectedItems.contains(1)) {
                                        // start download server
                                        if(!mSelectedItems.contains(0)) {
                                            Log.d(MainActivity.TAG,"only downloading");
                                            controlpath.sendGoodBye();
                                        }
                                        Log.d(MainActivity.TAG, "uri=" + uri);
                                        String server_ip = uri.substring(7, uri.indexOf(":", 7));
                                        int port_offset = Integer.parseInt(
                                                uri.substring(18, uri.indexOf(":", 18)));
                                        Log.d(MainActivity.TAG, server_ip + "," + port_offset);
                                        controlpath.sendDonwloadRequest(server_ip, myIP, 9000 + port_offset);
                                        Log.d(MainActivity.TAG, "Download data");
                                        FileServerAsyncTask task = new FileServerAsyncTask(
                                                getActivity(), 9000 + port_offset);//mContentView.findViewById(R.id.status_text)
                                        task.execute();
                                    }
                                }
                            });
                            dialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {

                                }

                            });
                            dialog.show();

                        }
                        else{
                            Log.d(MainActivity.TAG,"URI no match");
                        }
                    }
                    else{

                    }
                    break;
                case MSG_PORT:
                    //Check http server is running or not, preventing fake msg;
                    if(mServer!=null&&mServer.isRunning()){
                        ++listener;
                        ((TextView) mContentView.findViewById(R.id.status_text)).setText(Integer.toString(listener) + " peer is playing");
                    }
                    break;
                case MSG_BYE:
                    listener--;
                    if(listener <= 0)  //if listener's number equal to 0 which means no peer in this group want to watch this video, device will stop the http server
                    {
                        ((TextView) mContentView.findViewById(R.id.status_text)).setText("");
                        ((TextView) mContentView.findViewById(R.id.stop_server)).setVisibility(View.GONE);
                        ((TextView) mContentView.findViewById(R.id.btn_start_client)).setVisibility(View.VISIBLE);
                        if(mServer!=null)
                        {
                            mServer.stop();
                            mServer = null;
                        }
                    }
                    else
                        ((TextView) mContentView.findViewById(R.id.status_text)).setText(Integer.toString(listener) + " peer is playing");
                    break;
                case WARNING:
                    String warning = (String)msg.obj;
                    AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity());
                    dialog.setTitle("Warning");
                    dialog.setMessage(warning);
                    dialog.setCancelable(false);
                    dialog.setPositiveButton("I Know", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which){}
                    });
                    dialog.show();
                    break;
                default:
                    Log.w(MainActivity.TAG, "handleMessage: unexpected msg: " + msg.what);
            }

        }
    };
    /*
    A Thread class that operate sending MSG to specific IP
    Sending model is that waiting for response after sending one MSG.(Blocking model)
    Then, let function ProcessRequest to handle response. In most case, response would be the "PORT OK"
    */
    class Sendthread extends Thread {

        private String IP = null;
        private int port;
        private String msg = null;
        private int retrynum = 10;
        public Sendthread(String IP,String port,String msg){
            this.IP = IP;
            this.port = Integer.parseInt(port);
            this.msg =msg;
        }
        public Sendthread(String IP, int port,String msg){
            this.IP = IP;
            this.port = port;
            this.msg = msg;
        }

        @Override
        public void run() {
            BufferedWriter Writer;
            BufferedReader Reader;
            try {
                Socket socket = null;
                socket = new Socket(IP, port);
                socket.setSoTimeout(5000);
                Log.d(MainActivity.TAG, "creating new socket " + IP + "/ " + port);

                Reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                //peerScanner = new Scanner(peerIS);
                Writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                Writer.write(msg + '\n');  //Greeting formate : Hello:(ID addr)
                Writer.flush();
                String reply = Reader.readLine();
                Log.d(MainActivity.TAG,reply);
                int response = controlpath.ProcessRequest(reply,Writer);
                Writer.close();
                Reader.close();
                socket.close();
            }catch(IOException e){
                Log.e(MainActivity.TAG, e.getMessage());
                try {
                    Thread.sleep(50);
                    if(--retrynum>0)
                        run();
                    else
                        controlpath.peerIP.remove((String) IP);
                    return;
                }catch (InterruptedException error){
                    Log.e(MainActivity.TAG, error.getMessage());
                }
            }
        }
    }

    /*
     *Controlpath main task is listening msg from other peer and responsing with a "ack" msg.
     *Controlpath keeps a local copy in hash table of all IP addr got from SYN MGE sent by group owner.
     */
    public class Controlpath implements Runnable {
        //public static final String TAG = Controlpath.class.getName();
        private final static int HELLO = 0;
        private final static int PORT = 1;
        private final static int OK = 2;
        private final static int OTHER = 3;
        private boolean isOwner;
        private boolean isRunning = false;
        private String OwnerIP = null;
        private String myIP = null;
        private Thread thread = null;
        private int retrynum = 10 ;
        private HashSet<String> peerIP = new HashSet<String>();
        private ServerSocket serverSocket = null;

        public Controlpath(boolean isOwner,String IP){
            this.isOwner = isOwner;
            OwnerIP = IP;
        }

        public void start(){
            thread = new Thread(this);
            thread.start();
            isRunning = true;
        }
        public boolean isRunning(){return isRunning;}



        /*
         * if this device is not group owner, then send greeting msg to owner to let it know my IP address
         */
        public boolean init() throws IOException{
            BufferedReader Reader;
            BufferedWriter Writer;
            Socket socket =null;
            if (!isOwner) {
                try {
                    Log.d(MainActivity.TAG, "enter init");
                    Thread.sleep(400);
                    socket = new Socket(OwnerIP, 9000);
                    Log.d(MainActivity.TAG, "Opening control socket - ");
                    socket.setSoTimeout(5000);
                    Log.d(MainActivity.TAG, "Connection control socket");
                    Reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    myIP = socket.getLocalAddress().toString().substring(1);
                    Writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                    Writer.write("Hello:" + OwnerIP + '\n');  //Greeting formate : Hello:(ID addr)
                    Writer.flush();
                    String line = Reader.readLine();
                    Log.d(MainActivity.TAG,line);
                    Log.d(MainActivity.TAG, "get from local method: " + myIP);
                    Writer.close();
                    Reader.close();
                    socket.close();
                    Log.d(MainActivity.TAG, "socket closes");
                    peerIP.add(OwnerIP);
                } catch (InterruptedException e) {
                    Log.e(MainActivity.TAG, e.getMessage());
                }
            }
            else{
                myIP = OwnerIP;
            }
            handle.obtainMessage(MSG_IP,myIP).sendToTarget();
            return true;
        }
        @Override
        public void run()  {
            Log.d(MainActivity.TAG, " controlling thread is running");
            try {
                this.init();
            }catch (IOException e){
                Log.d(MainActivity.TAG,"Init failed, try again");
                run();
            }
            try{
                serverSocket = new ServerSocket(9000);
                while(isRunning){
                    Socket socket = serverSocket.accept();
                    String connectIP = socket.getRemoteSocketAddress().toString();
                    new Thread(new Listenthread(socket,connectIP)).start();
                }
                serverSocket.close();
                Log.d(MainActivity.TAG,"9000 port has been terminated");

            }catch (IOException e){
                Log.e(MainActivity.TAG, "controlpath:" + e.getMessage());
                Log.d(MainActivity.TAG, "accepting error");
                if(serverSocket!=null)
                    try {
                        serverSocket.close();
                    }catch (IOException error){
                        Log.e(MainActivity.TAG, e.getMessage() + "serversocket");
                    }

            }
        }

        public void stop() {
            isRunning = false;
            try {
                serverSocket.close();
            }catch (IOException e){
                Log.d(MainActivity.TAG,e.getMessage());
            }
            if (thread == null) {
                Log.e(MainActivity.TAG , "Control was stopped without being started.");
                return;
            }
            peerIP.clear();
            OwnerIP = null;
            myIP = null;
            Log.e(MainActivity.TAG, "Stopping Controlling.");
            thread.interrupt();
            thread = null;
            controlpath = null;
        }

        /*
         *Processing msg received from other peer and response to that.
         */
        public int ProcessRequest(String msg,BufferedWriter out){
            if(msg.contains("Hello:")) {                                                            //peer->owner greeting msg
                Log.d(MainActivity.TAG,"Received greeting msg from peer");
                return HELLO;
            }
            else if(msg.contains("PORT:")) {                                                        //server -> client Sending http server's PORT number. server doesn't has to be owner, clients are all device except itself
                String url = msg.substring(msg.indexOf("http://"));
                Log.d(MainActivity.TAG, "get http url from one peer in group: "+url);
                handle.obtainMessage(ACTIVE, url).sendToTarget();
                try {
                    out.write("PORT OK");
                    Log.d(MainActivity.TAG, "Reply to server");
                    out.flush();
                } catch (IOException e) {
                    Log.e(MainActivity.TAG, e.getMessage());
                }
                return OTHER;
            }
            else if(msg.contains("CUT:")){                                                          //server -> client Notifying other clients that the https server is no longer exists.
                Log.d(MainActivity.TAG,"Server wanna shut down server");
                return OTHER;
            }
            else if(msg.contains("GOODBYE:")){
                String cancelIP = msg.substring(msg.indexOf(':')+1);
                try{
                    out.write("PORT OK");
                    Log.d(MainActivity.TAG,cancelIP+"won't make request on this server");
                    out.flush();
                }catch (IOException e){
                    Log.e(MainActivity.TAG,e.getMessage());
                }
                handle.sendEmptyMessage(MSG_BYE);
                return OTHER;
            }
            else if(msg.contains("PORT OK")){                                                       //PORT OK: sck msg. Every msg need a response msg, or application would freeze.
                handle.sendEmptyMessage(MSG_PORT);
                Log.d(MainActivity.TAG,"receive OK ack");
                return OK;
            }
            else if(msg.contains("SYN")){                                                           //SYN:     peer <-owner   msg contains all peers' address in one group.
                msg = msg.substring(msg.indexOf(':')+1);
                Log.d(MainActivity.TAG,"synchronize IP set from owner "+msg);
                String IPs[] = msg.split("/");
                for(int i = 0 ; i < IPs.length ;++i){
                    if(myIP.compareTo(IPs[i])==0)
                        continue;
                    else if(IPs[i]!=""){
                        Log.d(MainActivity.TAG,"Here "+IPs[i]);
                        peerIP.add(IPs[i]);
                    }
                }
                try{
                    out.write("PORT OK");
                    Log.d(MainActivity.TAG,"Reply to server");
                    out.flush();
                }catch (IOException e){
                    Log.e(MainActivity.TAG,e.getMessage());
                }
            }
            else if(msg.contains("DOWNLOAD"))                                                       //DOWNLOAD: client -> server  requesting file transfer service
            {
                // send data ####### ADD IN CONTROL PATH
                String peer_ip = msg.substring(msg.indexOf("192"), msg.indexOf(":",12));
                int peer_port = Integer.parseInt(msg.substring(msg.indexOf(":",12) + 1));

                Intent serviceIntent = new Intent(getActivity(), FileTransferService.class);
                serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
                serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH, server_file_uri);
                // target address
                serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS, peer_ip);
                serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT, peer_port);
                getActivity().startService(serviceIntent);
                try{
                    out.write("PORT OK");
                    Log.d(MainActivity.TAG,"Reply to server");
                    out.flush();
                }catch (IOException e){
                    Log.e(MainActivity.TAG,e.getMessage());
                }
            }
            return OTHER;
        }

        public void sendIP(){
            Log.d(MainActivity.TAG,"Syn IP set");
            String IPset = "";
            for(Iterator it = peerIP.iterator();it.hasNext();){
                IPset += it.next().toString()+'/';
            }
            Log.d(MainActivity.TAG,IPset);
            for(Iterator it = peerIP.iterator(); it.hasNext();){
                Sendthread sendthread = new Sendthread(it.next().toString(),9000,"SYN:"+IPset);
                sendthread.start();
            }

        }
        public void sendPort(String httpuri){

            Log.d(MainActivity.TAG, httpuri);
            int time = 0;
            for(Iterator it = peerIP.iterator();it.hasNext();)
            {
                Sendthread msendthread = new Sendthread(it.next().toString(), 9000,"PORT:"+httpuri);
                msendthread.start();
            }
        }

        public void sendDonwloadRequest(String server_ip, String client_ip, int client_dl_port)
        {
            Log.d(MainActivity.TAG, "client" + client_ip + "downloadFile from server" + server_ip);
            Sendthread msendthread = new Sendthread(server_ip, 9000,"DOWNLOAD:"+client_ip+":"+client_dl_port);
            msendthread.start();
        }

        public void sendGoodBye(){
            if(currentplayingIP!=null){
                Sendthread msendthread = new Sendthread(currentplayingIP, 9000,"GOODBYE:"+myIP);
                Log.d(MainActivity.TAG, "GOODBYE"+myIP);
                msendthread.start();
                currentplayingIP = null;
            }

        }

        public class Listenthread implements Runnable{
            private Socket socket = null;
            public boolean isRunning = false;
            public String connectIP = null;

            public Listenthread(Socket socket,String connectIP){
                this.socket = socket;
                this.connectIP = connectIP;
            }
            @Override
            public void run(){
                String content = null;
                BufferedReader Reader;
                BufferedWriter Writer;
                try {
                    Reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    Writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                    String IP = socket.getRemoteSocketAddress().toString();
                    IP = IP.substring(IP.indexOf('/')+1,IP.indexOf(':'));
                    Log.d(MainActivity.TAG, IP);
                    peerIP.add(IP);
                    content = Reader.readLine();
                    Log.d(MainActivity.TAG, "Here is the msg: " + content);
                    int response = ProcessRequest(content,Writer);
                    if(response == HELLO) {
                        Writer.write("Hello:" + IP + "\n");
                        Log.d(MainActivity.TAG,"Hello:" + IP + "\n");
                        Writer.flush();
                    }
                    while(!socket.isClosed()) {
                        Writer.close();
                        Reader.close();
                        socket.close();
                        Log.d(MainActivity.TAG,"socket closes");
                    }
                    if(response==HELLO&&peerIP.size()>1&&info.isGroupOwner)
                    {
                        Log.d(MainActivity.TAG,"might be here");
                        sendIP();
                    }
                }catch (IOException e){
                    Log.e(MainActivity.TAG,e.getMessage());
                }
            }
        }

    }
    private class DataReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context,Intent intent){
            int data = intent.getIntExtra("listen",0);
            handle.sendEmptyMessage(MSG_BYE);
        }

    }
}
