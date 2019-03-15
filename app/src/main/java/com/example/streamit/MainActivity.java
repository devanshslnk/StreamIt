package com.example.streamit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.example.streamit.WiFiDirectBroadcastReceiver;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private final IntentFilter intentFilter = new IntentFilter();
    WifiP2pManager manager;
    WifiP2pManager.Channel channel;
    BroadcastReceiver receiver;
    ArrayList<WifiP2pDevice> peerList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Indicates a change in the Wi-Fi P2P status.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);

        // Indicates a change in the list of available peers.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);

        // Indicates the state of Wi-Fi P2P connectivity has changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

        // Indicates this device's details have changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);

        Button btn_discover= (Button)findViewById(R.id.discover_peers);
        Button btn_connect=(Button)findViewById(R.id.connect_peers);
        btn_connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectWithPeers();
            }
        });
        btn_discover.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                discoverPeers();
            }
        });


    }

    @Override
    protected void onResume(){
        super.onResume();
        registerReceiver(receiver,intentFilter);
    }

    @Override
    protected void onPause(){
        super.onPause();
        unregisterReceiver(receiver);
    }



    public  void discoverPeers(){
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener(){

            @Override
            public  void onSuccess(){
                System.out.println("discovered");
            }

            @Override
            public void onFailure(int reasonCode){
                System.out.println("reasonCode:"+Integer.toString(reasonCode));
            }
        });
    }

    public void connectWithPeers(){
        WifiP2pDevice device=this.peerList.get(0);

        WifiP2pConfig config=new WifiP2pConfig();
        config.deviceAddress=device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;
        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                System.out.println("Connected");
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(MainActivity.this, "Connect failed. Retry.",
                        Toast.LENGTH_SHORT).show();
                System.out.println("reason:"+Integer.toString(reason));
            }
        });

    }

    public  void setPeerList(ArrayList<WifiP2pDevice> peerList){
            this.peerList=peerList;
            System.out.println("peerlist");
            for(int i=0;i<this.peerList.size();i++){
                System.out.println(peerList.get(i));
            }
    }



    public void setIsWifiP2PEnabled(boolean value){
        System.out.println(value);
    }






}

