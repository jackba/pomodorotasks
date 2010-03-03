package com.kpz.pomodorotasks.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.kpz.pomodorotasks.map.TaskDatabaseMap;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TaskPanel {
	
	private static final int ONE_SEC = 1000;
	private static final int FIVE_MIN_IN_SEC = 300;

	private LinearLayout runTaskPanel;
	private ImageButton taskControlButton;
	private ImageButton hideButton;
	private TextView taskDescription;
	private TextView timeLeft;
	private ProgressBar progressBar;
	private TaskDatabaseMap taskDatabaseMap;
	private NotifyingService mBoundService;
	private TaskTimer counter;
	private ServiceConnection connection;
	private Activity activity;

	public TaskPanel(Activity pActivity, TaskDatabaseMap pTaskDatabaseMap) {
		runTaskPanel = (LinearLayout)pActivity.findViewById(R.id.runTaskPanel);
		taskControlButton = (ImageButton)pActivity.findViewById(R.id.control_icon);
		hideButton = (ImageButton)pActivity.findViewById(R.id.hide_panel_button);
		hideButton.setVisibility(View.VISIBLE);
		hideButton.setOnClickListener(new View.OnClickListener() {
			
			public void onClick(View v) {
				runTaskPanel.setVisibility(View.GONE);
			}
		});
		
    	taskDescription = (TextView)pActivity.findViewById(R.id.task_description);
    	timeLeft = (TextView)pActivity.findViewById(R.id.time_left);
    	progressBar = (ProgressBar)pActivity.findViewById(R.id.progress_horizontal);
    	addTaskButton = (Button) pActivity.findViewById(R.id.add_task_input_button);
    	taskDatabaseMap = pTaskDatabaseMap;
    	activity = pActivity;
	}

	public void startTask(String taskDescription) {

		initPanel(taskDescription);
		showPanel(taskDescription);
		resetTaskRun();
		beginTimeTask(taskDescription);
	}
	
	public void hidePanel(){
		
		runTaskPanel.setVisibility(View.GONE);
	}
	
	public void updateTaskDescription(String taskDesc) {
		
		taskDescription.setText(taskDesc);
	}
	
    public void refreshTaskPanel() {
    	
    	String text = getCurrentTaskText();
    	if (text == null || text.equals("")){
    		return;
    	}
    	
    	taskDescription.setText("");
    	
    	if (isTaskRunning()){
    		resetTaskRun();
    	}
	}
    
    public String getCurrentTaskText() {
		return taskDescription.getText().toString();
	}

	private void initPanel(final String ptaskDescription) {
		
    	taskControlButton.setOnClickListener(new View.OnClickListener() {

    	    public void onClick(View view) {

    	    	if (counter != null && taskControlButton.getTag(R.string.TASK_CONTROL_BUTTON_STATE_TYPE).equals(R.string.TO_STOP_STATE)){
    	    		resetTaskRun();
    	    	} else {
    	    		beginTimeTask(ptaskDescription);
    	    	}
    	    }
    	});
	}
	
	private void showPanel(String taskDesc) {
		
		runTaskPanel.setVisibility(View.VISIBLE);
		taskDescription.setText(taskDesc);
	}
	
	private void resetTaskRun() {
		if (counter != null){
    		counter.cancel();
    	}
		
		resetProgressControl();
	}
	
	private void resetProgressControl() {
		resetTimeLeftIfTaskNotRunning();
		progressBar.setProgress(0);
        taskControlButton.setImageResource(R.drawable.play);
        taskControlButton.setTag(R.string.TASK_CONTROL_BUTTON_STATE_TYPE, R.string.TO_PLAY_STATE);
        adjustDimensionsToDefault(taskControlButton);
        hideButton.setVisibility(View.VISIBLE);
	}
	
	private void adjustDimensionsToDefault(final ImageButton taskControlButton) {
		taskControlButton.getLayoutParams().height = addTaskButton.getHeight();
		taskControlButton.getLayoutParams().width = addTaskButton.getWidth();
	}

	public void resetTimeLeftIfTaskNotRunning() {
		
		if (isPanelVisible() && !isTaskRunning()){
			timeLeft.setText(taskDatabaseMap.fetchTaskDurationSetting() + ":00");	
		}
	}

	private boolean isPanelVisible() {
		return runTaskPanel.getVisibility() == View.VISIBLE;
	}
	
	private boolean isTaskRunning() {
		return progressBar.getProgress() != 0;
	}
	
	private void beginTimeTask(String taskDescription){
		
		int totalTime = taskDatabaseMap.fetchTaskDurationSetting() * 60;
		beginTask(taskDescription, totalTime, true);
	}
	
	private void beginBreakTask(){
		
		int totalTime = FIVE_MIN_IN_SEC;
		beginTask("Take a Break", totalTime, false);
	}
	
	private void beginTask(final String taskDesc, int totalTime, boolean isTimeTask) {
	
		taskDescription.setText(taskDesc);
		
		progressBar.setMax(totalTime);
		counter = new TaskTimer(totalTime * ONE_SEC, ONE_SEC, beepHandler, isTimeTask);
		//counter = new ProgressThread(handler);
		counter.start();
		
		hideButton.setVisibility(View.INVISIBLE);
		taskControlButton.setImageResource(R.drawable.stop);
		taskControlButton.setTag(R.string.TASK_CONTROL_BUTTON_STATE_TYPE, R.string.TO_STOP_STATE);

	    connection = new ServiceConnection() {

			public void onServiceConnected(ComponentName className, IBinder service) {
	            // This is called when the connection with the service has been
	            // established, giving us the service object we can use to
	            // interact with the service.  Because we have bound to a explicit
	            // service that we know is running in our own process, we can
	            // cast its IBinder to a concrete class and directly access it.
	            mBoundService = ((NotifyingService.LocalBinder)service).getService();
	            mBoundService.notifyTimeStarted(taskDesc);
	        }

	        public void onServiceDisconnected(ComponentName className) {
	            // This is called when the connection with the service has been
	            // unexpectedly disconnected -- that is, its process crashed.
	            // Because it is running in our same process, we should never
	            // see this happen.
	            mBoundService = null;
	        }
	    };
		
		activity.bindService(new Intent(activity, 
				NotifyingService.class), 
				connection, 
				Context.BIND_AUTO_CREATE);
		
	}

    public class TaskTimer extends CountDownTimer{
	    
		private Handler mHandler;
		private boolean isTaskTime;

		public TaskTimer(long millisInFuture, long countDownInterval, Handler handler, boolean isTaskTime) {
	    	super(millisInFuture + ONE_SEC, countDownInterval);
	    	this.mHandler = handler;
	    	this.isTaskTime = isTaskTime;
		}

		@Override
	    public void onTick(long millisUntilFinished) {
	    	
	    	incrementProgress(millisUntilFinished);
	    }

		private void incrementProgress(long millisUntilFinished) {
			
			final DateFormat dateFormat = new SimpleDateFormat("mm:ss");
            String timeStr = dateFormat.format(new Date(millisUntilFinished - ONE_SEC));
            timeLeft.setText(timeStr);
           	progressBar.setProgress(new Long(millisUntilFinished / ONE_SEC).intValue());
           	
           	if (timeStr.equals("00:00")){
           		beep();
    	    	endTimer();
           	}
		}

		private void beep() {
			Message msg = mHandler.obtainMessage();
			Bundle bundle = new Bundle();
			bundle.putBoolean("TASK_TIME", isTaskTime);
			msg.setData(bundle);
			mHandler.sendMessage(msg);
		}
		
		private void endTimer() {
			cancel();
		}
		@Override
		public void onFinish() {
			// do nothing
		}
    }

    final Handler beepHandler = new Handler() {
        public void handleMessage(Message msg) {

        	mBoundService.notifyTimeEnded();
	        
	    	resetProgressControl();
	    	
	    	boolean isTaskTime = msg.getData().getBoolean("TASK_TIME");
	    	if(isTaskTime){
	    		
	        	final String[] items = {"Take 5 min break", "Cancel"};
	    		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
	    		builder.setItems(items, new DialogInterface.OnClickListener() {
	    		    public void onClick(DialogInterface dialog, int item) {
	    		        
	    		    	//Toast.makeText(getApplicationContext(), items[item], Toast.LENGTH_SHORT).show();
	    		    	switch (item) {
	    				case 0:
	    					beginBreakTask();
	    					break;

	    				case 1:
	    			        break;
	    			        
	    				default:
	    					break;
	    				}
	    		        
	    		    }
	    		});
	    		AlertDialog alert = builder.create();
	    		alert.show();
	    		
	    	} else {
	    		
	    		taskDescription.setText("");
	    		if(mBoundService != null){
	    			mBoundService.clearTaskNotification();
	    		}
	    	}
        }
    };
	private Button addTaskButton;

}