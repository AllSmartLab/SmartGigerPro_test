package ftlab.kr.smartgigetpro_test;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.TedPermission;

import java.util.ArrayList;

import kr.ftlab.lib.SmartSensor;
import kr.ftlab.lib.SmartSensorEventListener;
import kr.ftlab.lib.SmartSensorResultGE;


public class MainActivity extends Activity implements SmartSensorEventListener {
    private SmartSensor mMI;
    private SmartSensorResultGE mResultGE;

    private Button btnStart;
    private TextView txtResultRV;
    private TextView txtResultCPM;
    private TextView txtResultC;
    private TextView txtResultT;
    private RadioGroup RG;
    private RadioButton RB;

    int mProcess_Status = 0;//초기값은 0
    int Process_Stop = 0;   //미결합일 경우 0
    int Process_Start = 1;  //값을 1로 자는 이유는 단자의 결합유무 확인

    String SelectMode;
    String TimeLoad;

    //실행시간 측정을 위한 변수 선언
    final static int Init = 0;
    final static int Pause = 1;
    final static int StopMin = 180000;
    int cur_Status = Init; //현재의 상태를 저장할변수를 초기화함.
    long myBaseTime;
    long myPauseTime;

    private static final int UI_UPDATE = 0;


    BroadcastReceiver mHeadSetConnectReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equalsIgnoreCase(Intent.ACTION_HEADSET_PLUG)) { //이어폰 단자에 센서 결함 유무 확인
                if (intent.hasExtra("state")) {
                    if (intent.getIntExtra("state", 0) == 0) { //센서 분리 시
                        stopSensing();
                        btnStart.setEnabled(false); //센서가 분리되면 START/STOP 버튼 비활성화. 클릭 불가
                        Toast.makeText(MainActivity.this, "Sensor not found.", Toast.LENGTH_SHORT).show();
                    } else if (intent.getIntExtra("state", 1) == 1) { //센서 결합 시
                        Toast.makeText(MainActivity.this, "Sensor find.", Toast.LENGTH_SHORT).show();
                        btnStart.setEnabled(true); //센서가 결합되면 START/SOP 버튼 활성화
                    }
                }
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); //어플이 실행되는 동안 스마트폰 디스플레이 항상 켜지도록 설정

        btnStart = (Button) findViewById(R.id.StartButton); // start/stop 버튼 생성
        RG = (RadioGroup) findViewById(R.id.RG); //시간 설정을 위한 라디오그룹 생성
        txtResultRV = (TextView) findViewById(R.id.RValue); //RV값 텍스트뷰 생성
        txtResultCPM = (TextView) findViewById(R.id.CMPValue); //CMP값 텍스트뷰 생성
        txtResultC = (TextView) findViewById(R.id.CountValue); //Count값 텍스트뷰 생성
        txtResultT = (TextView) findViewById(R.id.TimerValue); //Timer값 텍스트뷰 생성

        mMI = new SmartSensor(MainActivity.this, this);
        mMI.selectDevice(SmartSensor.GE_PRO);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {//마시멜로 이상 버전에서는 권한을 별도로 요청함. 유저가 미허용시, 어플 사용 못함
            //퍼미션 체크
            PermissionListener permissionlistener = new PermissionListener() {
                @Override
                public void onPermissionGranted() {
                }

                @Override
                public void onPermissionDenied(ArrayList<String> deniedPermissions) {//유저가 정상적으로 권한 허용 했을 경우
                    finish();

                }
            };

            new TedPermission(this)
                    .setPermissionListener(permissionlistener)
                    .setDeniedMessage("If you reject permission,you can not use this service\n\nPlease turn on permissions at [Setting] > [Permission]")
                    .setPermissions(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.MODIFY_AUDIO_SETTINGS, android.Manifest.permission.WRITE_EXTERNAL_STORAGE).check();
        }
    }


    public void mOnClick(View v) {
        int id = RG.getCheckedRadioButtonId();
        RB = (RadioButton) findViewById(id);
        SelectMode = RB.getText().toString();    //선택한 라디오버튼의 텍스트 값을 저장

        if (mProcess_Status == Process_Start) {//버튼 클릭 시 상태가 Start면 stop process 수행
            stopSensing();
        } else {//버튼 클릭시 상태가 Stop면 start process 수행
            startSensing();
        }
        switch (cur_Status) {
            case Init:
                myBaseTime = SystemClock.elapsedRealtime();
                myTimer.sendEmptyMessageDelayed(0, 100);
                cur_Status = Pause;
                break;
            case Pause:
                myTimer.removeMessages(0); //핸들러 메세지 제거
                myPauseTime = SystemClock.elapsedRealtime();
                cur_Status = Init;
                break;
        }
    }

    Handler myTimer = new Handler() {
        public void handleMessage(Message msg) {
            txtResultT.setText(getTimeOut());
            TimeLoad = getTimeOut();
            //비어있는 메세지를 Handler 에게 전송
            myTimer.sendEmptyMessage(0);
        }
    };


    String getTimeOut() {//현재시간을 계속 구해서 출력하는 메소드
        long now = SystemClock.elapsedRealtime(); //실행 후 경과 시간
        long outTime = now - myBaseTime;
        String easy_outTime = String.format("%02d:%02d:%02d", outTime / 1000 / 60, (outTime / 1000) % 60, (outTime % 1000) / 10);
        return easy_outTime;
    }


    Handler TimerStopHandler = new Handler() {//타이머제한 핸들러
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UI_UPDATE:
                    stopSensing();
                    ;
                    myTimer.removeMessages(0); //핸들러 메세지 제거
                    myPauseTime = SystemClock.elapsedRealtime();
                    cur_Status = Init;
            }
        }
    };


    public void startSensing() {
        btnStart.setText("STOP");
        mProcess_Status = Process_Start; //현재 상태를 start로 설정
        mMI.start(); //측정 시작

        if (SelectMode.equals("3min") == true) {    //3min을 선택할 경우
            TimerStopHandler.sendEmptyMessageDelayed(UI_UPDATE, StopMin);   //3분뒤 핸들러메세지를 전송하여 작동상태 중지.
        }
    }

    public void stopSensing() {
        btnStart.setText("START");
        mProcess_Status = Process_Stop; //현재 상태를 stop로 설정
        mMI.stop(); //측정 종료
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_calibration) {
            mMI.registerSelfConfiguration(); //보정값 초기화
            mMI.start();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter intflt = new IntentFilter();
        intflt.addAction(Intent.ACTION_HEADSET_PLUG);//오디오 등록
        this.registerReceiver(mHeadSetConnectReceiver, intflt);//센서 결합 유무 판단 위해 BroadcastReceiver 등록
    }

    @Override
    protected void onPause() {
        super.onPause();
        this.unregisterReceiver(mHeadSetConnectReceiver);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {//어플 종료 시 수행
        mMI.quit();
        finish();
        System.exit(0);
        super.onDestroy();
    }

    @Override
    public void onMeasured() {
        MeasureHandler.sendEmptyMessageDelayed(UI_UPDATE, 100);
    }

    Handler MeasureHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UI_UPDATE:
                    String str = "";
                    mResultGE = mMI.getResultGE();//측정된 값을 가져옴

                    str = String.format("%1.0f", mResultGE.GE_uSv);//측정값은 RV
                    txtResultRV.setText(str);
                    str = String.format("%1.0f", mResultGE.GE_CPM);//측정값은 CPM
                    txtResultCPM.setText(str);
                    str = String.format("%d", mResultGE.GE_CNT);//측정값은 Count(CPM / 2)
                    txtResultC.setText(str);
            }
        }
    };


    @Override
    public void onSelfConfigurated() {//어플을 설치하고 최초 실행시에만 보정 진행, 보정이 끝난 후 호출, 측정 시작 상태
        mProcess_Status = 0;
        btnStart.setText("STOP");

    }
}
