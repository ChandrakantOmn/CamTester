package com.sri.testlib;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.rtdriver.driver.Contants;
import com.rtdriver.driver.HsBluetoothPrintDriver;
import com.rtdriver.driver.HsComPrintDriver;
import com.rtdriver.driver.HsUsbPrintDriver;
import com.rtdriver.driver.HsWifiPrintDriver;
import com.rtdriver.driver.LabelBluetoothPrintDriver;
import com.rtdriver.driver.LabelUsbPrintDriver;
import com.rtdriver.driver.LabelWifiPrintDriver;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Observable;
import java.util.Observer;

/**
 * Created by Administrator on 2015/5/28.
 */
public class PrintDataContainActivity extends Activity implements View.OnClickListener, Observer {

    public static int HS_PAGER_TYPE = 0; // 0=58mm,  1=80mm
    private static int mConnState = Contants.UNCONNECTED;
    private static final int PRINT_FINISH_HANDLER_FLAG = 7174;

    private final String TAG = getClass().getSimpleName();

    private boolean mUsbRegistered = false;//表示UsbDeviceReceiver是否已被注册
    private Context mContext;

    public static final int COM_MODE = 0x04;

    private TextView llConnectSpinner;//蓝牙，usb，wifi设置
    private RadioGroup rgConnectMode;
    private RadioGroup rg_print_image;
    private TextView tv_hs_connect_disconnect;//连接或断开  connect or disconnect
    TextView dataTv, tv_heat_sensitive_connect;
    public static final String DATAPREF = "Datapref";
    public static final String DATA = "datakey";
    private int hsPagerType;
    private ConnStateObservable mConnStateObservable;
    private static ConnResultObservable mConnResultObservable;

    private ForegroundColorSpan unConnectedColorSpan;
    private ForegroundColorSpan connectedColorSpan;
     String labelSizeStr;
    private ArrayList<HsComReceive> hsComReceiveArrayList = new ArrayList<>();
     boolean isPrintFinish;
     PrintFinishHandler printFinishHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        mConnStateObservable = ConnStateObservable.getInstance();
        mConnResultObservable = ConnResultObservable.getInstance();
        initDriver();
        setContentView(R.layout.acitivty_print_data_contain);
        ConnResultObservable.getInstance().addObserver(this);
        mConnStateObservable = ConnStateObservable.getInstance();
        initView();
    }

    @SuppressLint("CutPasteId")
    private void initView() {
        llConnectSpinner = findViewById(R.id.heat_sensitive_setting_connect_spinner);
        dataTv = findViewById(R.id.datatv);
        llConnectSpinner.setHint(R.string.tip_choose_usb_device);
        rgConnectMode = findViewById(R.id.rg_heat_sensitive_setting_connect_mode);
        rg_print_image = findViewById(R.id.rg_print_image);
        tv_hs_connect_disconnect = findViewById(R.id.tv_connect);
        View printText = findViewById(R.id.printText);
        printText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                textPrint(dataTv.getText().toString());
            }
        });

        SharedPreferences prefs = getSharedPreferences(DATAPREF, MODE_PRIVATE);
        String finalist = prefs.getString(DATA, "No data available");
        tv_heat_sensitive_connect = findViewById(R.id.tv_connect);
        tv_heat_sensitive_connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getConnState() != Contants.UNCONNECTED) {
                    disconnect();
                    tv_hs_connect_disconnect.setText(R.string.connect);
                } else {
                    connectCOM();
                }

            }
        });

        dataTv.setText(finalist);
        setListener();
        TextView tv_ver = findViewById(R.id.tv_ver);
        tv_ver.setVisibility(View.GONE);
        switch ((int) SPUtils.get(mContext, "Papertype", Contants.TYPE_58)) {
            case Contants.TYPE_80:
                rg_print_image.check(R.id.rg_print_image_80);
                HS_PAGER_TYPE = Contants.TYPE_80;
                break;
            case Contants.TYPE_58:
                rg_print_image.check(R.id.rg_print_image_58);
                HS_PAGER_TYPE = Contants.TYPE_58;
                break;
        }
    }

    private void disconnect() {
        HsComPrintDriver.getInstance().disConnect();
    }

    public void onClick(View view) {
    //    textPrint(dataTv.getText().toString());
    }

    private void textPrint(String s) {
        final HsComPrintDriver hsComPrintDriver = HsComPrintDriver.getInstance();
        hsComPrintDriver.Begin();
        hsComPrintDriver.SetDefaultSetting();
        //0x00表示左对齐，0x01表示居中，0x02表示右对齐
        byte flagAlignMode = 0x01;
        hsComPrintDriver.SetAlignMode(flagAlignMode);
        //英文字体模式
        byte flagCharacterMode = 0x00;
        hsComPrintDriver.SetCharacterPrintMode(flagCharacterMode);
        //0x00表示解除下划线，0x01下划线宽度为1，0x02下划线宽度为2
        byte flagUnderLineMode = 0x00;
        hsComPrintDriver.SetUnderline(flagUnderLineMode);
        hsComPrintDriver.SelChineseCodepage();
        //中文字体模式
        byte flagChineseCharacterMode = 0x00;
        hsComPrintDriver.SetChineseCharacterMode(flagChineseCharacterMode);
        hsComPrintDriver.setCharsetName("GBK");
        hsComPrintDriver.WriteCmd(s);
        hsComPrintDriver.LF();
        hsComPrintDriver.CR();
        hsComPrintDriver.LF();
        hsComPrintDriver.CR();
        hsComPrintDriver.LF();
        hsComPrintDriver.CR();
        hsComPrintDriver.LF();
        hsComPrintDriver.CR();
        hsComPrintDriver.LF();
        hsComPrintDriver.CR();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogUtils.d(TAG, "onDestroy");
        ConnResultObservable.getInstance().deleteObserver(this);
        if (mUsbRegistered) {
            // unregisterReceiver(mUsbReceiver);
            mUsbRegistered = false;
        }
    }


    private void setListener() {
        RadioButton rb = findViewById(R.id.rb_heat_sensitive_setting_com);
        rb.setChecked(true);
        rgConnectMode.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.rb_heat_sensitive_setting_com) {//For AP02 ，串口模式
                    notifyModeChanged();
                    rg_print_image.check(R.id.rg_print_image_58);
                }
            }
        });

        rgConnectMode.check(R.id.rb_heat_sensitive_setting_com);

        rg_print_image.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.rg_print_image_58) {
                    hsPagerType = Contants.TYPE_58;
                } else if (checkedId == R.id.rg_print_image_80) {
                    hsPagerType = Contants.TYPE_80;
                }
                SPUtils.put(mContext, "Papertype", hsPagerType);
            }
        });

    }


    private void notifyModeChanged() {
        llConnectSpinner.setHint(R.string.connect_com_device);
    }


    private void connectCOM() {//串口连接 for AP02
        Log.d("jhvhjg", "nj");
        try {
            // HsComPrintDriver.getInstance().connect();
            HsComPrintDriver printer = HsComPrintDriver.getInstance();
            printer.connect();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "Some thing went wrong..!!", Toast.LENGTH_LONG).show();
        }
    }


    @Override
    public void update(Observable observable, final Object data) {
        if (observable == ConnStateObservable.getInstance()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tv_hs_connect_disconnect.setText((CharSequence) data);
                }
            });
        } else if (observable == ConnResultObservable.getInstance()) {
            switch ((int) data) {
                case Contants.FLAG_FAIL_CONNECT:
                    ToastUtil.show(mContext, getString(R.string.fail_connect));
                    llConnectSpinner.setText("");
                    tv_hs_connect_disconnect.setText(R.string.connect);
                    break;
                case Contants.FLAG_SUCCESS_CONNECT:
                    ToastUtil.show(mContext, getString(R.string.success_connect));
                    tv_hs_connect_disconnect.setText(R.string.disconnect_btn);
                    break;
            }
        }
    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }

    public static int getConnState() {
        return mConnState;
    }

    private void setConnState(int connectState) {
        if (mConnState != connectState) {
            mConnState = connectState;
            mConnStateObservable.setChanged();
            mConnStateObservable.notifyObservers(getConnStateString());
        }
    }

    public CharSequence getConnStateString() {
        if (mConnState == Contants.UNCONNECTED) {
            String unConnectedStr = getResources().getString(R.string.unconnected);
            SpannableString connStateUnConnectedString = new SpannableString(unConnectedStr);
            if (unConnectedColorSpan == null) {
                unConnectedColorSpan = new ForegroundColorSpan(Color.RED);
            }
            connStateUnConnectedString.setSpan(unConnectedColorSpan, 0, unConnectedStr.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            return connStateUnConnectedString;
        } else {
            String connectedStr = getResources().getString(R.string.connected);
            SpannableString connStateConnectedString = new SpannableString(connectedStr);
            if (connectedColorSpan == null) {
                connectedColorSpan = new ForegroundColorSpan(Color.GREEN);
            }
            connStateConnectedString.setSpan(connectedColorSpan, 0, connectedStr.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            return connStateConnectedString;
        }
    }

    private void initDriver() {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HsUsbPrintDriver.getInstance().setUsbManager(usbManager);
        LabelUsbPrintDriver.getInstance().setUsbManager(usbManager);

        ConnStateHandler connStateHandler = new ConnStateHandler();
        HsBluetoothPrintDriver.getInstance().setHandler(connStateHandler);
        HsUsbPrintDriver.getInstance().setHandler(connStateHandler);
        HsWifiPrintDriver.getInstance().setHandler(connStateHandler);
        HsComPrintDriver.getInstance().setHandler(connStateHandler);
        LabelBluetoothPrintDriver.getInstance().setHandler(connStateHandler);
        LabelUsbPrintDriver.getInstance().setHandler(connStateHandler);
        LabelWifiPrintDriver.getInstance().setHandler(connStateHandler);
        printFinishHandler = new PrintFinishHandler();
        String labelWidth = Objects.requireNonNull(SPUtils.get(this, "labelWidth", "80")).toString();
        String labelHeight = Objects.requireNonNull(SPUtils.get(this, "labelHeight", "30")).toString();
        labelSizeStr = labelWidth + "*" + labelHeight;
    }

    @SuppressLint("HandlerLeak")
    class ConnStateHandler extends Handler {
        @Override
        public void handleMessage(@NotNull Message msg) {
            super.handleMessage(msg);
            Bundle data = msg.getData();
            switch (data.getInt("flag")) {
                case Contants.FLAG_COM_RECEIVE_DATA:
                    if (hsComReceiveArrayList.size() > 0 && msg.obj != null) {
                        for (int i = 0; i < hsComReceiveArrayList.size(); i++) {
                            hsComReceiveArrayList.get(i).onReceiveMsg((byte[]) msg.obj);
                        }
                    }
                    break;
                case Contants.FLAG_STATE_CHANGE:
                    setConnState(data.getInt("state"));
                    break;
                case Contants.FLAG_FAIL_CONNECT:
                    mConnResultObservable.setChanged();
                    mConnResultObservable.notifyObservers(Contants.FLAG_FAIL_CONNECT);
                    break;
                case Contants.FLAG_SUCCESS_CONNECT:
                    mConnResultObservable.setChanged();
                    mConnResultObservable.notifyObservers(Contants.FLAG_SUCCESS_CONNECT);
                    break;
            }
        }
    }

    private interface HsComReceive {
        void onReceiveMsg(byte[] bytes);

    }

    @SuppressLint("HandlerLeak")
    private class PrintFinishHandler extends Handler {
        @Override
        public void handleMessage(@NotNull Message msg) {
            super.handleMessage(msg);
            Bundle data = msg.getData();
            if (data.getInt("flag") == PRINT_FINISH_HANDLER_FLAG) {
                int inquiry_status = data.getInt("state", 0) & 0xFF;
                Log.d("inquiry_status------", inquiry_status + "");
                isPrintFinish = inquiry_status == 0x80;
            }
        }
    }

}
