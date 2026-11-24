package com.earlink.lab;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothStatusCodes;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.*;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer,CheckBox> boxes = new LinkedHashMap<>();
    private final Map<Integer,AudioDeviceInfo> outputs = new LinkedHashMap<>();
    private final List<Trial> trials = new ArrayList<>();
    private final StringBuilder history = new StringBuilder();
    private AudioManager audio;
    private LinearLayout root, deviceList;
    private TextView capabilities, report, status;
    private Button start, stop, refresh, confirm, deny;
    private boolean running, eligible;
    private long begun;
    private String lastRoutes = "";
    private static final int RATE=48000, SECONDS=15;
    private static class Trial {
        final AudioDeviceInfo target; final AudioTrack track; final boolean accepted;
        Trial(AudioDeviceInfo d, AudioTrack t, boolean a) { target=d;track=t;accepted=a; }
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView label(String s, int size){TextView v=new TextView(this); v.setText(s);v.setTextSize(size);v.setTextColor(Color.rgb(225,233,245));v.setPadding(0,dp(7),0,dp(7));root.addView(v);return v;}
    private Button button(String s, Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setOnClickListener(v->r.run());root.addView(b);return b;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);audio=getSystemService(AudioManager.class);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(26),dp(20),dp(32));root.setBackgroundColor(Color.rgb(17,24,39));scroll.addView(root);setContentView(scroll);
        label("EARLINK / LAB",13);label("Direct audio sharing",28);
        label("Pair your headphones in Android settings. Select at least two media outputs and run a quiet 15-second test. Keep system media volume low. No microphone or internet access.",15);
        capabilities=label("",14);
        button("Open Bluetooth settings",()->{stopTest("Stopped before settings");startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));});
        button("Allow Bluetooth access",()->{if(Build.VERSION.SDK_INT>=31&&!permitted())requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},7);else scan();});
        refresh=button("Refresh connected outputs",this::scan);
        label("Available media outputs",20);
        deviceList=new LinearLayout(this);deviceList.setOrientation(LinearLayout.VERTICAL);root.addView(deviceList);
        start=button("Start simultaneous tone test",this::startTest);
        stop=button("Stop / restore normal playback",()->stopTest("Stopped by user"));stop.setEnabled(false);
        status=label("Ready",16);
        confirm=button("Both / all selected earphones audible",()->answer(true));confirm.setEnabled(false);
        deny=button("Not all earphones audible",()->answer(false));deny.setEnabled(false);
        label("Routing diagnostics",20);report=label("",13);report.setTextIsSelectable(true);
        button("Copy diagnostic report",()->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("EarLink report",capabilities.getText()+"\n"+history));Toast.makeText(this,"Report copied",Toast.LENGTH_SHORT).show();});
        if(Build.VERSION.SDK_INT>=31&&!permitted())requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},7);
    }
    private boolean permitted(){return Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;}
    @Override protected void onResume(){super.onResume();scan();}
    @Override public void onRequestPermissionsResult(int request,String[] p,int[] results){super.onRequestPermissionsResult(request,p,results);scan();}
    private void log(String s){history.append(s).append('\n');report.setText(history.toString());}
    private boolean mediaBluetooth(AudioDeviceInfo d){int t=d.getType();return t==AudioDeviceInfo.TYPE_BLUETOOTH_A2DP||(Build.VERSION.SDK_INT>=31&&(t==AudioDeviceInfo.TYPE_BLE_HEADSET||t==AudioDeviceInfo.TYPE_BLE_SPEAKER));}
    private String desc(AudioDeviceInfo d){return d==null?"unknown/not routed":d.getProductName()+" [id="+d.getId()+", type="+d.getType()+"]";}
    private String feature(int n){return n==BluetoothStatusCodes.FEATURE_SUPPORTED?"supported":n==BluetoothStatusCodes.FEATURE_NOT_SUPPORTED?"not supported":"unknown (code "+n+")";}
    private void scan(){
        if(running)return;
        Set<Integer> selected=new HashSet<>();for(Map.Entry<Integer,CheckBox> e:boxes.entrySet())if(e.getValue().isChecked())selected.add(e.getKey());
        outputs.clear();boxes.clear();deviceList.removeAllViews();
        StringBuilder c=new StringBuilder(Build.MANUFACTURER+" "+Build.MODEL+" • Android "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")\n");
        BluetoothAdapter adapter=getSystemService(BluetoothManager.class).getAdapter();
        if(adapter==null)c.append("No Bluetooth adapter\n");
        else if(!permitted())c.append("Bluetooth permission not granted\n");
        else try{
            c.append("Bluetooth: ").append(adapter.isEnabled()?"on":"off").append('\n');
            if(Build.VERSION.SDK_INT>=33){c.append("LE Audio: ").append(feature(adapter.isLeAudioSupported())).append('\n');c.append("LE broadcast source: ").append(feature(adapter.isLeAudioBroadcastSourceSupported())).append('\n');}else c.append("LE broadcast check requires Android 13+\n");
            c.append("Paired devices (not necessarily connected): ").append(adapter.getBondedDevices().size()).append('\n');
        }catch(RuntimeException e){c.append("Bluetooth query failed: ").append(e.getClass().getSimpleName()).append('\n');}
        for(AudioDeviceInfo d:audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS))if(mediaBluetooth(d)){
            outputs.put(d.getId(),d);CheckBox b=new CheckBox(this);b.setText(desc(d));b.setTextColor(Color.WHITE);b.setChecked(selected.contains(d.getId()));boxes.put(d.getId(),b);deviceList.addView(b);
        }
        c.append("Exposed Bluetooth media outputs: ").append(outputs.size());capabilities.setText(c.toString());
        if(outputs.size()<2){TextView v=new TextView(this);v.setText("Android exposes fewer than two media outputs. Pair/connect a second headset and refresh. This may indicate a platform limit.");v.setTextColor(Color.LTGRAY);deviceList.addView(v);}
        start.setEnabled(outputs.size()>=2);
    }
    private short[] tone(){short[] a=new short[RATE*2];for(int i=0;i<a.length;i++){double t=i/(double)RATE;double p=t%1.0;double env=p<.04?p/.04:p<.45?1:p<.49?(.49-p)/.04:0;a[i]=(short)(32767*.08*env*Math.sin(2*Math.PI*440*t));}return a;}
    private void startTest(){
        if(running)return;
        List<AudioDeviceInfo> targets=new ArrayList<>();for(Map.Entry<Integer,CheckBox> e:boxes.entrySet())if(e.getValue().isChecked())targets.add(outputs.get(e.getKey()));
        if(targets.size()<2){status.setText("Select at least two different outputs.");return;}
        history.setLength(0);report.setText("");eligible=false;confirm.setEnabled(false);deny.setEnabled(false);lastRoutes="";
        short[] pcm=tone();log("Requested outputs: "+targets.size());
        try{
            for(AudioDeviceInfo target:targets){
                AudioTrack t=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.length*2).build();
                boolean registered=false;
                try{
                    if(t.getState()!=AudioTrack.STATE_INITIALIZED)throw new IllegalStateException("Track initialization failed");
                    boolean accepted=t.setPreferredDevice(target);trials.add(new Trial(target,t,accepted));registered=true;
                    if(t.write(pcm,0,pcm.length)!=pcm.length)throw new IllegalStateException("Short PCM write");
                    if(t.setLoopPoints(0,pcm.length,-1)!=AudioTrack.SUCCESS)throw new IllegalStateException("Loop setup failed");
                    t.setVolume(.25f);log(desc(target)+" preference accepted="+accepted);
                }finally{if(!registered)t.release();}
            }
            for(Trial tr:trials)tr.track.play();
            running=true;begun=SystemClock.elapsedRealtime();start.setEnabled(false);stop.setEnabled(true);refresh.setEnabled(false);for(CheckBox b:boxes.values())b.setEnabled(false);handler.post(poll);
        }catch(RuntimeException e){log("Test error: "+e);stopTest("Test failed; tracks released");}
    }
    private final Runnable poll=new Runnable(){public void run(){
        if(!running)return;
        long elapsed=SystemClock.elapsedRealtime()-begun;StringBuilder s=new StringBuilder();Set<Integer> actual=new HashSet<>();boolean matched=true;
        try{for(Trial tr:trials){AudioDeviceInfo routed=tr.track.getRoutedDevice();s.append("Requested ").append(tr.target.getId()).append(" -> ").append(desc(routed)).append('\n');if(routed!=null)actual.add(routed.getId());if(routed==null||routed.getId()!=tr.target.getId())matched=false;}}
        catch(RuntimeException e){log("Route query failed: "+e);stopTest("Routing query failed");return;}
        String routes=s.toString();if(!routes.equals(lastRoutes)){log("At "+elapsed+" ms:\n"+routes);lastRoutes=routes;}
        if(elapsed>=2000){eligible=true;confirm.setEnabled(true);deny.setEnabled(true);}
        status.setText((matched&&actual.size()==trials.size()?"Distinct requested routes reported; listen to confirm.":"Requested routes not all verified; listen to confirm.")+"\n"+Math.max(0,SECONDS-elapsed/1000)+" seconds left");
        if(elapsed>=SECONDS*1000){log("Final route snapshot: "+(matched?"matches requested routes":"does not match all requested routes"));stopTest("Test complete. Record what you heard.");}else handler.postDelayed(this,500);
    }};
    private void answer(boolean yes){if(!eligible)return;log("Manual listening result: "+(yes?"all selected audible (user reported)":"not all selected audible"));confirm.setEnabled(false);deny.setEnabled(false);eligible=false;if(running)stopTest("Listening result recorded");else status.setText("Listening result recorded");}
    private void stopTest(String reason){handler.removeCallbacks(poll);running=false;for(Trial tr:trials){try{tr.track.stop();}catch(RuntimeException ignored){}try{tr.track.release();}catch(RuntimeException ignored){}}trials.clear();if(stop!=null){stop.setEnabled(false);refresh.setEnabled(true);start.setEnabled(outputs.size()>=2);for(CheckBox b:boxes.values())b.setEnabled(true);status.setText(reason);}}
    @Override protected void onPause(){if(running)stopTest("Stopped when app left foreground");super.onPause();}
    @Override protected void onDestroy(){stopTest("Closed");super.onDestroy();}
}
