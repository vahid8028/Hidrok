package com.example.CosyDVR;

import android.app.Service;
import android.app.Notification;
//import android.support.v4.app.NotificationCompat;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.Gravity;
import android.view.SurfaceView;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.AudioManager;
import android.os.Environment;
import android.text.format.DateFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Binder;
import android.os.Message;
import android.os.SystemClock;
import android.os.Bundle;

import java.util.Date;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Timer;
import java.util.TimerTask;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.String;
import android.os.PowerManager;



public class BackgroundVideoRecorder extends Service implements SurfaceHolder.Callback, MediaRecorder.OnInfoListener, LocationListener  {
//CONSTANTS-OPTIONS
	public long MAX_TEMP_FOLDER_SIZE = 10000000;
	public long MIN_FREE_SPACE = 1000000;
	public int MAX_VIDEO_DURATION = 600000;
	public int VIDEO_WIDTH = 1280;
	public int VIDEO_HEIGHT= 720;
	public int MAX_VIDEO_BIT_RATE = 5000000;
	public int REFRESH_TIME = 1000;
	public String VIDEO_FILE_EXT = ".mp4";
	public String SRT_FILE_EXT = ".srt";
	public String GPX_FILE_EXT = ".gpx";
	//public int AUDIO_SOURCE = CAMERA;
	
    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();
    private WindowManager windowManager = null ;
    private SurfaceView surfaceView = null;
    private Camera camera = null;
    private MediaRecorder mediaRecorder = null;
    private boolean isrecording = false;
    private boolean isflashon = false;
    private boolean isnight = false;
    private int isfavorite = 0;
    private int focusmode = 0;
    private String currentfile = null;

    private SurfaceHolder mSurfaceHolder = null;
    private PowerManager.WakeLock mWakeLock = null;
    private Timer mTimer = null;
    private TimerTask mTimerTask = null;

    public TextView mTextView = null;
    public TextView mSpeedView = null;
    public long mSrtCounter = 0;
    public Handler mHandler = null;
    
    private File mSrtFile = null;
    private File mGpxFile = null;
    private OutputStreamWriter mSrtWriter = null;
    private OutputStreamWriter mGpxWriter = null;
    private long mSrtBegin = 0;
    private long mNewFileBegin = 0;

    private LocationManager mLocationManager = null;
    private long mPrevTim = 0;
    
    private String[] mFocusModes = {Parameters.FOCUS_MODE_INFINITY,
    							 Parameters.FOCUS_MODE_CONTINUOUS_VIDEO,
    							 //Parameters.FOCUS_MODE_FIXED,
    							 //Parameters.FOCUS_MODE_EDOF,
    							 Parameters.FOCUS_MODE_MACRO};
    
    private final class HandlerExtension extends Handler {
		public void handleMessage(Message msg) {
			if (!isrecording) {
				return;
			}
			String srt = new String();
			String gpx = new String();
			Date datetime = new Date();
			long now = mSrtBegin;
			int hour = (int)((now - mNewFileBegin)/(1000*60*60));
			int min =  (int)((now - mNewFileBegin)%(1000*60*60)/(1000*60));
			int sec =  (int)((now - mNewFileBegin)%(1000*60)/(1000));
			int mil =  (int)((now - mNewFileBegin)%(1000));
		    srt = srt + String.format("%d\n%02d:%02d:%02d,%03d --> ", mSrtCounter, hour, min, sec, mil);

			now = SystemClock.elapsedRealtime();
			hour = (int)((now - mNewFileBegin)/(1000*60*60));
			min =  (int)((now - mNewFileBegin)%(1000*60*60)/(1000*60));
			sec =  (int)((now - mNewFileBegin)%(1000*60)/(1000));
			mil =  (int)((now - mNewFileBegin)%(1000));
		    srt = srt + String.format("%02d:%02d:%02d,%03d\n", hour, min, sec, mil);
		    srt = srt + DateFormat.format("yyyy-MM-dd_kk-mm-ss", datetime.getTime()).toString() + "\n";

		    // Get the location manager
		    //Criteria criteria = new Criteria();
		    //String bestProvider = mLocationManager.getBestProvider(criteria, false);
		    //Location location = mLocationManager.getLastKnownLocation(bestProvider);
		    Location location = null;
		    try {
		    	location = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		    } catch (Exception e) {};

//		    Location location = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		    
		    double lat=-1,lon=-1,alt=-1;
		    float spd=0,acc=-1;
		    int sat=0;
		    long tim=0;

            try {
            lat = location.getLatitude();
            lon = location.getLongitude();
            tim = location.getTime()/1000; //millisec to sec
	        alt = location.getAltitude();
	        acc = location.getAccuracy();
	        spd = location.getSpeed() * 3.6f;
	        sat = location.getExtras().getInt("satellites");
                
            } catch (NullPointerException e) {
            	tim = 0;
            }

            srt = srt + String.format("lat:%1.6f,lon:%1.6f,alt:%1.0f,spd:%1.1fkm/h,acc:%01.1fm,sat:%d,tim:%d\n\n", lat, lon, alt, spd, acc, sat, tim);
            gpx = gpx + String.format("<trkpt lon=\"%f\" lat=\"%f\">\n", lon, lat);
            gpx = gpx + String.format("<ele>%1.0f</ele>\n", alt);
            gpx = gpx + "<time>" + DateFormat.format("yyyy-MM-dd", datetime.getTime()).toString() + "T" + DateFormat.format("kk:mm:ss", datetime.getTime()).toString() + "Z</time>\n";
            gpx = gpx + "</trkpt>\n";
			if(mPrevTim==0 && tim!=mPrevTim){
				mPrevTim = tim;
			}
	   	    try {
	   	    	mSrtWriter.write(srt);
	   	    	if(tim != mPrevTim) {
	   	    		mGpxWriter.write(gpx);		   	    		
	   	    	}
	   	    } catch(IOException e){};
	   	    mTextView.setText(srt);
	   	    if(tim != mPrevTim){
	   	    	mSpeedView.setText(String.format("%1.1f",spd));
	   	    	mPrevTim = tim;
	   	    } else {
	   	    	mSpeedView.setText(String.format("---"));
	   	    }
	   	    mSrtBegin = now;
		}
	}

	/**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
    	BackgroundVideoRecorder getService() {
            // Return this instance of LocalService so clients can call public methods
            return BackgroundVideoRecorder.this;
        }
    }
 
    @Override
    public void onCreate() {
        // Start foreground service to avoid unexpected kill
	        Notification notification = new Notification.Builder(this)
	            .setContentTitle("CosyDVR Background Recorder Service")
	            .setContentText("")
	            .setSmallIcon(R.drawable.cosydvricon)
	            .build();
	        startForeground(1, notification);

        // Create new SurfaceView, set its size to 1x1, move it to the top left corner and set this service as a callback
        windowManager = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
         surfaceView = new SurfaceView(this);
        LayoutParams layoutParams = new WindowManager.LayoutParams(
        	//WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
        	1,1,
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        windowManager.addView(surfaceView, layoutParams);

        mTextView = new TextView(this);
        layoutParams = new WindowManager.LayoutParams(
            	WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            );
        layoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        windowManager.addView(mTextView, layoutParams);
        mTextView.setTextColor(Color.parseColor("#FFFFFF"));
        mTextView.setShadowLayer(5,0,0,Color.parseColor("#000000"));
        mTextView.setText("--");

        mSpeedView = new TextView(this);
        layoutParams = new WindowManager.LayoutParams(
            	WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            );
        layoutParams.gravity = Gravity.RIGHT | Gravity.TOP;
        windowManager.addView(mSpeedView, layoutParams);
        mSpeedView.setTextColor(Color.parseColor("#CC6666"));
        mSpeedView.setShadowLayer(5,0,0,Color.parseColor("#000000"));
        mSpeedView.setTextSize(56);
        mSpeedView.setText("---");

        surfaceView.getHolder().addCallback(this);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CosyDVRWakeLock");

        mHandler = new HandlerExtension();

        startGps();
    }
    
    // Method called right after Surface created (initializing and starting MediaRecorder)
    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
    	mSurfaceHolder = surfaceHolder;
    }

    public int getFocusMode(){
    	return focusmode;
    }

    public int isFavorite(){
    	return isfavorite;
    }
    
    public boolean isRecording(){
    	return isrecording;
    }

    public void StopRecording() {
    	if (isrecording){
	    	StopResetReleaseLock();
	    	mTimer.cancel();
	    	mTimer.purge();
	    	mTimer = null;
	    	mTimerTask.cancel();
	    	mTimerTask = null;
	    	try {
				mGpxWriter.write("</trkseg>\n"
						+"</trk>\n"
						+"</gpx>"); //GPX footer

		    	mSrtWriter.flush();
		    	mSrtWriter.close();
		    	mGpxWriter.flush();
		    	mGpxWriter.close();
	    	} catch(IOException e) {};
	
	    	if(currentfile != null && isfavorite != 0) {
	    		File tmpfile = new File(Environment.getExternalStorageDirectory() + "/CosyDVR/temp/" + currentfile + VIDEO_FILE_EXT);
	    		File favfile = new File(Environment.getExternalStorageDirectory() + "/CosyDVR/fav/" + currentfile + VIDEO_FILE_EXT);
	    		tmpfile.renameTo(favfile);
	    		tmpfile = new File(Environment.getExternalStorageDirectory() + "/CosyDVR/temp/" + currentfile + SRT_FILE_EXT);
	    		favfile = new File(Environment.getExternalStorageDirectory() + "/CosyDVR/fav/" + currentfile + SRT_FILE_EXT);
	    		tmpfile.renameTo(favfile);
	    		tmpfile = new File(Environment.getExternalStorageDirectory() + "/CosyDVR/temp/" + currentfile + GPX_FILE_EXT);
	    		favfile = new File(Environment.getExternalStorageDirectory() + "/CosyDVR/fav/" + currentfile + GPX_FILE_EXT);
	    		tmpfile.renameTo(favfile);
				if(isfavorite == 1) {
					isfavorite = 0;
				}
	    	}
	    	isrecording = false;
    	}
    }
    public void Restartrecording() {
    	AudioManager manager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    	manager.setStreamSolo(AudioManager.STREAM_SYSTEM,true);
    	manager.setStreamMute(AudioManager.STREAM_SYSTEM,true);
    	StopRecording();
    	StartRecording();
    	freeSpace();
    	manager.setStreamMute(AudioManager.STREAM_SYSTEM,false);
    	manager.setStreamSolo(AudioManager.STREAM_SYSTEM,false);
    }
    
	public void StartRecording() {
		OpenUnlockPrepareStart();
		Parameters parameters = camera.getParameters();
		//parameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
		parameters.setFocusMode(Parameters.FOCUS_MODE_INFINITY);

		//parameters.setFocusMode(Parameters.FOCUS_MODE_FIXED); 
		if(!isnight){
    		parameters.setSceneMode(Parameters.SCENE_MODE_AUTO);
    	} else {
    		parameters.setSceneMode(Parameters.SCENE_MODE_NIGHT);
    	}

		camera.setParameters(parameters);
		mSrtCounter = 0;
		mSrtFile = new File(Environment.getExternalStorageDirectory() + "/CosyDVR/temp/" + currentfile + SRT_FILE_EXT);
		mSrtFile.setWritable(true);
		mGpxFile = new File(Environment.getExternalStorageDirectory() + "/CosyDVR/temp/" + currentfile + GPX_FILE_EXT);
		mGpxFile.setWritable(true);
		try {
			mSrtWriter = new FileWriter(mSrtFile);
			mGpxWriter = new FileWriter(mGpxFile);
			mGpxWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
					+"<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" version=\"1.1\" creator=\"CosyDVR\">\n"
					+"<trk>\n"
					+"<trkseg>\n"); //header
		} catch(IOException e) {};
		mNewFileBegin = SystemClock.elapsedRealtime(); 
		mSrtBegin = mNewFileBegin;

        mTimer = new Timer();
        mTimerTask = new TimerTask() {
        	@Override
        	public void run() {
        		// What you want to do goes here
            	mSrtCounter++;
        		mHandler.obtainMessage(1).sendToTarget();
        	}
        };
        mTimer.scheduleAtFixedRate(mTimerTask, 0, REFRESH_TIME);
    	isrecording = true;
	}

	private void StopResetReleaseLock() {
		if(isrecording) {
			mediaRecorder.stop();
		    mediaRecorder.reset();
		    mediaRecorder.release();
		
		    camera.lock();
		    camera.release();
		    mWakeLock.release();
		}
	}

	private void OpenUnlockPrepareStart() {
		if(!isrecording) {
			mWakeLock.acquire();
		    camera = Camera.open();
		    mediaRecorder = new MediaRecorder();
		    camera.unlock();
		
		    mediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
		    mediaRecorder.setCamera(camera);
	
		    mediaRecorder.setMaxDuration(this.MAX_VIDEO_DURATION);
		    mediaRecorder.setMaxFileSize(0); // 0 - no limit
		  //mediaRecorder.setOnErrorListener
		    mediaRecorder.setOnInfoListener(this);
	
	
		    mediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
		    mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
		
		    mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
		
		    mediaRecorder.setAudioEncoder(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH).audioCodec);//MediaRecorder.AudioEncoder.HE_AAC
		    mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
		
		    currentfile = DateFormat.format("yyyy-MM-dd_kk-mm-ss", new Date().getTime()).toString();
		    mediaRecorder.setOutputFile(Environment.getExternalStorageDirectory() + "/CosyDVR/temp/" + currentfile + VIDEO_FILE_EXT);
		
		    mediaRecorder.setAudioChannels(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH).audioChannels);
		    mediaRecorder.setAudioSamplingRate(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH).audioSampleRate);
		    mediaRecorder.setAudioEncodingBitRate(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH).audioBitRate);
		
		    mediaRecorder.setVideoEncodingBitRate(this.MAX_VIDEO_BIT_RATE);
		    mediaRecorder.setVideoSize(this.VIDEO_WIDTH,this.VIDEO_HEIGHT);// 640x480,800x480
		    mediaRecorder.setVideoFrameRate(30);
		
		    
		    try { 
		    	mediaRecorder.prepare();
		    } catch (Exception e) {}
		    mediaRecorder.start();
		}
	}
	
	public void freeSpace() {
		File dir = new File(Environment.getExternalStorageDirectory() + "/CosyDVR/temp/");
		File[] filelist = dir.listFiles();
		Arrays.sort(filelist, new Comparator<File>() {
            public int compare(File f1, File f2) {
                return Long.valueOf(f2.lastModified()).compareTo(
                        f1.lastModified());
            }
        });
			long totalSize = 0; //in kB
			int i;
		for(i = 0; i < filelist.length; i++){
			totalSize += filelist[i].length()/1024;
		}
		i = filelist.length - 1;
//		if(Build.VERSION.SDK_INT >= 11) {
			while (i > 0 && (totalSize > this.MAX_TEMP_FOLDER_SIZE || dir.getFreeSpace() < this.MIN_FREE_SPACE)) {
				totalSize -= filelist[i].length()/1024;
				filelist[i].delete();
				i--;
			}
//		} else {
//			while (i > 0 && (totalSize > this.MAX_TEMP_FOLDER_SIZE)) {
//				totalSize -= filelist[i].length()/1024;
//				filelist[i].delete();
//				i--;
//			}
//		}
	}

	public void toggleFocus() {
    	focusmode = (focusmode + 1) % mFocusModes.length;
		Parameters parameters = camera.getParameters();
    	parameters.setFocusMode(mFocusModes[focusmode]);
    	camera.setParameters(parameters);
    }

	public void toggleNight() {
    	if(camera != null){
    		Parameters parameters = camera.getParameters();
        	parameters.setFocusMode(mFocusModes[focusmode]);	//restore chosen focus mode
	    	if(isnight){
	    		parameters.setSceneMode(Parameters.SCENE_MODE_AUTO);
	    		isnight = false;
	    	} else {
	    		parameters.setSceneMode(Parameters.SCENE_MODE_NIGHT);
	            isnight = true;
	    	}
	    	camera.setParameters(parameters);
    	}
    }

	public void toggleFavorite() {
    	isfavorite = (isfavorite + 1) % 3;
    }

    public void toggleRecording() {
    	if(isrecording){
    		StopRecording();
    	} else {
    		StartRecording();
    	}
    }

    public void toggleFlash() {
    	if(camera != null){
    		Parameters parameters = camera.getParameters();
	    	if(isflashon){
	    		parameters.setFlashMode(Parameters.FLASH_MODE_OFF);
	    		isflashon = false;
	    	} else {
	    		parameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
	            isflashon = true;
	    	}
	    	camera.setParameters(parameters);
    	}
    }

    public void ChangeSurface(int width, int height){
    	if(this.VIDEO_WIDTH/this.VIDEO_HEIGHT > width/height) {
    		height = (int) (width * this.VIDEO_HEIGHT / this.VIDEO_WIDTH);
    	}
    	else {
    		width = (int) (height * this.VIDEO_WIDTH / this.VIDEO_HEIGHT);
    	}
        LayoutParams layoutParams = new WindowManager.LayoutParams(
            	//WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            	width,height,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            );
        if (width == 1) {
            layoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        } else {
        	layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        }
		windowManager.updateViewLayout(surfaceView, layoutParams);
	    if(width>1) {
	    	mTextView.setVisibility(TextView.VISIBLE);
	    	mSpeedView.setVisibility(TextView.VISIBLE);
	    } else {
	    	mTextView.setVisibility(TextView.INVISIBLE);
	    	mSpeedView.setVisibility(TextView.INVISIBLE);
	    }
    }

    // Stop isrecording and remove SurfaceView
    @Override
    public void onDestroy() {
    	StopRecording();

    	stopGps();
        windowManager.removeView(surfaceView);
        windowManager.removeView(mTextView);
        windowManager.removeView(mSpeedView);
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {}

    @Override
    public IBinder onBind(Intent intent) { return mBinder; }

    @Override
    public void onInfo(MediaRecorder mr, int what, int extra) {                     
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
            this.Restartrecording();
        }          
    }

    public void onLocationChanged(Location location) {
        // Doing something with the position...
    }       
    public void onProviderDisabled(String provider) {}
    public void onProviderEnabled(String provider) {}
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    private void startGps() {
      if (mLocationManager == null)
          mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
      if (mLocationManager != null) {
      	try {
              mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1, 1, (LocationListener) this);   //mintime,mindistance
      	} catch (Exception e) {
      	    Log.e("CosyDVR", "exception: " + e.getMessage());             
      	    Log.e("CosyDVR", "exception: " + e.toString());
      	}
             
      }       
  }

  private void stopGps() {
      if (mLocationManager != null)
          mLocationManager.removeUpdates((LocationListener) this);
      mLocationManager = null;
  }
}