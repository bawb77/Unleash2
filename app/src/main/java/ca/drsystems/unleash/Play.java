package ca.drsystems.unleash;

import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pServiceRequest;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;



public class Play extends FragmentActivity implements WifiP2pManager.ConnectionInfoListener {

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    public joinFrag jf = new joinFrag();
    FragmentManager fragmentManager = getSupportFragmentManager();
    FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();

    private final IntentFilter intentFilter = new IntentFilter();
    private List<WifiP2pDevice> peersAvailable = new ArrayList();
    private List<WifiP2pDevice> peersConnected = new ArrayList();
    private boolean isWifiP2pEnabled;

    Channel mChannel;
    Context context;
    TextView numplayer;
    WifiP2pManager mManager;
    WifiDirectBroadcastReceiver receiver;
    PeerListListener peerListListener;
    WifiP2pServiceInfo serviceInfo;
    Handler handler;
    boolean deviceServiceStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_play);
        setUpMapIfNeeded();
        joinFragStart();

        handler = new Handler();
        context = this;
        initializeIntents();
        DeviceHolder.devices = new ArrayList<WifiP2pDevice>();
        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);
        deviceServiceStarted = false;
        createPeerListListener();
    }

    public void joinFragStart() {
        fragmentTransaction.replace(R.id.map, jf).commit();
    }

    public void letsPlay(View v) {
        findViewById(R.id.joinReadyFrag).setVisibility(View.INVISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();

        receiver = new WifiDirectBroadcastReceiver(mManager, mChannel, this, peerListListener);
        registerReceiver(receiver, intentFilter);
        Log.v("P2P", "WifiDirectBroadcastReceiver registered");

        initializeDiscovery();
    }

    @Override
    public void onPause() {
        unregisterReceiver(receiver);
        mManager.removeGroup(mChannel, new ActionListener() {
            @Override
            public void onSuccess() {
                Log.v("P2P", "Group Removed");
            }

            @Override
            public void onFailure(int reason) {
                Log.v("P2P", "Group Not Removed: " + reason);
            }
        });
        super.onPause();
    }

//    @Override
//    public void onStop(){
//        super.onStop();
//        onDestroy();
//    }

    @Override
    public void onDestroy() {
        mManager.removeGroup(mChannel, new ActionListener() {
            @Override
            public void onSuccess() {
                Log.v("P2P", "Group Removed");
            }

            @Override
            public void onFailure(int reason) {
                Log.v("P2P", "Group Not Removed: " + reason);
            }
        });
        // Disconnect from wifi to avoid channel conflict
        //mWifiManager.disconnect();
        super.onDestroy();
    }

    public void initializeIntents() {
        //  Indicates a change in the Wi-Fi P2P status.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);

        // Indicates a change in the list of available peers.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);

        // Indicates the state of Wi-Fi P2P connectivity has changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

        // Indicates this device's details have changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        Log.v("P2P", "Initialized Intents");
    }

    private void initializeDiscovery() {

        mManager.addServiceRequest(mChannel,
                WifiP2pServiceRequest.newInstance(serviceInfo.SERVICE_TYPE_VENDOR_SPECIFIC),
                new ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.v("P2P", "Added service!");
                    }

                    @Override
                    public void onFailure(int reason) {
                        Log.v("P2P", "Did not add service: " + reason);
                    }
                });

        mManager.discoverServices(mChannel, new ActionListener() {

            @Override
            public void onSuccess() {
                Log.v("P2P", "Peer Discovery Initialized");
                Toast.makeText(Play.this, "Looking for friends!",
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reasonCode) {
                Log.v("P2P", "Peer Discovery Failed To Initialize");
                Toast.makeText(Play.this, "Error finding friends, code : " + reasonCode,
                        Toast.LENGTH_SHORT).show();

                if (reasonCode == WifiP2pManager.NO_SERVICE_REQUESTS) {

                    // initiate a stop on service discovery
                    mManager.stopPeerDiscovery(mChannel, new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            // initiate clearing of the all service requests
                            mManager.clearServiceRequests(mChannel, new WifiP2pManager.ActionListener() {
                                @Override
                                public void onSuccess() {
                                    // reset the service listeners, service requests, and discovery
                                    initializeDiscovery();
                                }

                                @Override
                                public void onFailure(int i) {
                                    Log.d("P2P", "FAILED to clear service requests ");
                                }
                            });
                        }

                        @Override
                        public void onFailure(int i) {
                            Log.d("P2P", "FAILED to stop discovery");
                        }
                    });
                }
            }
        });
    }

    private void createPeerListListener() {
        Log.v("P2P", "Creating PeerListListener");
        peerListListener = new WifiP2pManager.PeerListListener() {
            @Override
            public void onPeersAvailable(WifiP2pDeviceList peerList) {

                Log.v("P2P", "onPeersAvailable() call");
                // Out with the old, in with the new.
                peersAvailable.clear();
                peersAvailable.addAll(peerList.getDeviceList());

                numplayer = (TextView) findViewById(R.id.numPlayers);
                numplayer.setText("" + peersAvailable.size());

//                WifiManager wifiM = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
//                List<ScanResult> tempList = wifiM.getScanResults();
//                boolean isThere = false;
//                for (ScanResult iter : tempList) {
//                    if ((iter.SSID).contains("DIRECT")) {
//                        isThere = true;
//                    }
//                }
//                if (isThere){
//                    connect();
//                    startClientDeviceService();
//                }else if(!isThere){
//                    createGroup();
//                }
                createGroup();

                // If an AdapterView is backed by this data, notify it
                // of the change.  For instance, if you have a ListView of available
                // peers, trigger an update.
                /*((WiFiPeerListAdapter) getListAdapter()).notifyDataSetChanged();
                if (peers.size() == 0) {
                    Log.d(WiFiDirectActivity.TAG, "No devices found");
                    return;
                }*/
            }
        };
    }

    public void createGroup() {

        mManager.requestConnectionInfo(mChannel, new WifiP2pManager.ConnectionInfoListener() {
            @Override
            public void onConnectionInfoAvailable(WifiP2pInfo info) {

                //Goes through each device in the ArrayList "peers" and connects to it
                    //Creates the group if it hasn't been formed yet
                    mManager.createGroup(mChannel, new ActionListener() {
                        @Override
                        public void onSuccess() {
                            Log.v("P2P", "Group formed successfully!");

                            mManager.requestConnectionInfo(mChannel, new WifiP2pManager.ConnectionInfoListener() {
                                @Override
                                public void onConnectionInfoAvailable(WifiP2pInfo info) {
                                    Log.v("P2P", "Connection Info: " + info);
                                    if (info.isGroupOwner) {
                                        Log.v("P2P", "createGroup onSuccess groupOwner");

                                        startHostService();
                                        connect();
                                    } else {
                                        Log.v("P2P", "createGroup onSuccess !groupOwner");

                                        startClientDeviceService();
                                    }
                                }

                            });
                        }

                        @Override
                        public void onFailure(int reason) {
                            Log.v("P2P", "Group formation failed: " + reason);
                            if (reason == 2) {
                                mManager.requestConnectionInfo(mChannel,
                                        new WifiP2pManager.ConnectionInfoListener() {

                                    @Override
                                    public void onConnectionInfoAvailable(WifiP2pInfo info) {
                                        if (info.isGroupOwner) {
                                            Log.v("P2P", "createGroup onFailure groupOwner");

                                            if (!deviceServiceStarted){
                                                Log.v("SOCK", "HostService started: " +
                                                        deviceServiceStarted);
                                                startHostService();
                                            }

                                            connect();

                                        } else if (!deviceServiceStarted) {
                                            Log.v("P2P", "createGroup onFailure !groupOwner");
                                            startClientDeviceService();
                                        }
                                    }
                                });
                            }
                        }
                    });
            }
        });
        deviceServiceStarted = true;
    }

    public void connect() {
        for (WifiP2pDevice device : peersAvailable) {


            final WifiP2pDevice device1 = device;

            if (!peersConnected.contains(device1)) {

                Log.v("P2P", "Connecting to device: " + device.deviceName +
                        " with address: " + device.deviceAddress);

                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device1.deviceAddress;
                config.wps.setup = WpsInfo.PBC;


                mManager.connect(mChannel, config, new ActionListener() {

                    @Override
                    public void onSuccess() {
                        // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
                        Log.v("P2P", "Connection to: " + device1.deviceName +
                                " initiated");
                        peersConnected.add(device1);
                        peersAvailable.remove(device1);
                    }

                    @Override
                    public void onFailure(int reason) {
                        Log.v("P2P", "Connection to: " + device1.deviceName +
                                " initiation failed: " + reason);
                    }
                });
            }
        }
        DeviceHolder.devices = peersConnected;

    }

    private void startHostService(){
        Log.v("SOCK", "Starting HostService");
        new HostService(handler, this).execute();
    }

    private void startClientDeviceService(){
        Log.v("SOCK", "Starting ClientDeviceService");
        Thread thread = new Thread(new Runnable(){
            @Override
            public void run() {
                try {
                    Log.v("SOCK", "DeviceHolder info: " + DeviceHolder.info.groupOwnerAddress.toString());
                    new ClientDeviceService(handler, Play.this, 12345, DeviceHolder.info.groupOwnerAddress).execute();
                } catch (Exception e) {
                    Log.v("SOCK", "startClientListener: Exception");
                    e.printStackTrace();
                }
            }
        });

        thread.start();
    }

    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    public boolean isWifiP2pEnabled() {
        return isWifiP2pEnabled;
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        // InetAddress from WifiP2pInfo struct.
        InetAddress groupOwnerAddress = info.groupOwnerAddress;

        // After the group negotiation, we can determine the group owner.
        if (info.groupFormed && info.isGroupOwner) {
            // Do whatever tasks are specific to the group owner.
            // One common case is creating a server thread and accepting
            // incoming connections.
            Log.v("P2P", "You are the group owner");
            Log.v("P2P", "Connection established!");
        } else if (info.groupFormed) {
            // The other device acts as the client. In this case,
            // you'll want to create a client thread that connects to the group
            // owner.
            Log.v("P2P", "Connection established!");
        }
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p/>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p/>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        mMap.addMarker(new MarkerOptions().position(new LatLng(0, 0)).title("Marker"));
    }

    public static class DeviceHolder {

        public static List<WifiP2pDevice> devices;
        public static WifiP2pInfo info;
    }

    public static class UserLocations {

        static HashMap<Integer, User> userLoc = new HashMap<Integer, User>();
        static boolean there = false;
        static int myUser;

        public static HashMap<Integer, User> returnList() {

            Log.v("UL", "UserLocations returnList()");
            return userLoc;
        }

        public static void setUser(User user) {

            Log.v("UL", "UserLocations setUser()");
            there = false;
            if (user.getNumber() != 4) {
                userLoc.put(user.getNumber(), user);
            }

        }

        public static User getUser(int i) {
            return userLoc.get(i);
        }

        public static int getMyUser() {

            Log.v("UL", "UserLocations getMyUser: " + myUser);
            return myUser;
        }

        public static void setMyUser(int i) {
            myUser = i;
            Log.v("UL", "UserLocations setMyUser: " + myUser);
        }
    }
}