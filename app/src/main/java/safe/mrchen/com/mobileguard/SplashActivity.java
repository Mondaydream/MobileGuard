package safe.mrchen.com.mobileguard;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.RotateAnimation;
import android.view.animation.ScaleAnimation;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.lidroid.xutils.HttpUtils;
import com.lidroid.xutils.http.ResponseInfo;
import com.lidroid.xutils.http.callback.RequestCallBack;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import safe.mrchen.com.mobileguard.domain.VersionBean;

public class SplashActivity extends AppCompatActivity {

    private static final int ERROR = 1;
    private static final int SUCCESS = 2;
    private        TextView       mTv_versionCode;
    private        TextView       mTv_versionName;
    private        RelativeLayout mRl_splash;
    private        AnimationSet   mAnimationSet;
    private static int            mVersionCode;
    private static VersionBean    mVersionBean;
    /**=================    继承activity以后写这个为了在静态内部类中使用上下文     =================*/
    private static Context        mContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;

        initView();
        initDate();
        startAnimation();
        initEvent();
    }

    private void initEvent() {
        mAnimationSet.setAnimationListener(new MyAnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                super.onAnimationStart(animation);
                checkVersion();
            }
        });
    }

    private static class MyHandler extends Handler {
        private final WeakReference<SplashActivity> mActivity;

        public MyHandler(SplashActivity activity) {
            mActivity = new WeakReference<SplashActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            SplashActivity activity = mActivity.get();
            if (activity != null) {
                switch (msg.what) {
                    case SUCCESS:
//                        成功就判断版本
                        if (mVersionCode == mVersionBean.versionCode) {
                            loadMain();
                        } else {
                            //有新版本更新操作
                            showNewVersionDialog();
                        }
                        break;
                    case ERROR:
                        int error_code = msg.arg1;
                        switch (error_code) {
                            case 10081:
                                Toast.makeText(mContext, "url错误", Toast.LENGTH_SHORT).show();
                                break;
                            case 10082:
                                Toast.makeText(mContext, "io错误或者是服务器没有开启", Toast.LENGTH_SHORT).show();
                                break;
                            case 10083:
                                Toast.makeText(mContext, "json错误", Toast.LENGTH_SHORT).show();
                                break;

                            default:
                                Toast.makeText(mContext, "资源错误", Toast.LENGTH_SHORT).show();
                                break;
                        }
                    default:
                        loadMain();
                        break;
                }
            }
        }
    }
    //展示新版本的对话框
    private static void showNewVersionDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(mContext);
        dialog.setTitle("提示 ：")
                .setMessage(mVersionBean.desc)
                .setPositiveButton("更新", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
//下载新版本，使用xutils
                        downLoadNewApk();
                    }
                })
                .setNegativeButton("下次再说", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        loadMain();
                    }
                })
                .show();
    }

    /**
     * 下载新的apk使用xutils
     */
    private static void downLoadNewApk() {
        HttpUtils httpUtils = new HttpUtils();
        httpUtils.configSoTimeout(5000);
        //封装了子线程访问数据，主线程处理结果
        httpUtils.download(mVersionBean.downLoadUrl, Environment.getExternalStorageDirectory() + "/sjws.apk", new RequestCallBack<File>() {
            @Override
            public void onSuccess(ResponseInfo<File> responseInfo) {
                showUpdateApkDialog();
            }

            @Override
            public void onFailure(com.lidroid.xutils.exception.HttpException e, String s) {
                Toast.makeText(mContext, "下载失败", Toast.LENGTH_SHORT).show();
                System.out.println("---------------" + e+":-->"+s);
                loadMain();
            }
        });
    }

    /**
     * 显示是否安装的对话框
     */
    private static void showUpdateApkDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(mContext);
        dialog.setTitle("提示 ：")
                .setMessage("是否安装？")
                .setPositiveButton("安装", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        System.out.println("---------------" + "安装更新");
                    }
                })
                .setNegativeButton("下次再说", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        loadMain();
                    }
                })
                .show();
    }

    private static void loadMain() {
        Intent intent = new Intent(mContext, HomeActivity.class);
        mContext.startActivity(intent);
    }

    private final MyHandler mHandler = new MyHandler(this);

    /**
     * 检查版本信息，在子线程进行
     */
    private void checkVersion() {
        new Thread() {
            @Override
            public void run() {
                super.run();
                Message msg = mHandler.obtainMessage();
                msg.what = ERROR;
                long startTime = System.currentTimeMillis();
                try {
                    //获取下载的url地址，从自己创建的配置文件中获取
                    URL url = new URL(getString(R.string.versionurl));
                    System.out.println("---------------" + url);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setRequestMethod("GET");
                    int responseCode = conn.getResponseCode();
                    System.out.println("---------------" + responseCode);
                    if (responseCode == 200) {
                        //访问成功通过IO获取信息，解析json数据，判断是否有新版本
                        String jsonStr = stream2string(conn.getInputStream());
//                        解析json数据
                        mVersionBean = parseJson(jsonStr);

//判断是否有新版本，给个对话框提示，但是子线不能处理和ui有关的事情，
// 所以发送handler处理;因为错误有多种，发消息做分类，注意发送位置
//                        Message msg = mHandler.obtainMessage();
//                        mHandler.sendMessage(msg);
                        msg.what = SUCCESS;

                    } else {
                        msg.arg1 = responseCode;
                    }
                } catch (MalformedURLException e) {
                    msg.arg1 = 10081;
                } catch (IOException e) {
                    msg.arg1 = 10082;
                } catch (JSONException e) {
                    msg.arg1 = 10083;
                } finally {
                    long endTime = System.currentTimeMillis();
                    if (endTime - startTime < 2000) {
                        SystemClock.sleep(2000 - (startTime - endTime));
                    }
                    mHandler.sendMessage(msg);
                }
            }
        }.start();

    }

    /**
     * @param jsonStr 解析json数据
     * @return
     */
    private VersionBean parseJson(String jsonStr) throws JSONException {
        JSONObject object = new JSONObject(jsonStr);
        VersionBean bean = new VersionBean();
        bean.downLoadUrl = object.getString("apkdownloadurl");
        bean.versionName = object.getString("versionname");
        bean.versionCode = object.getInt("versioncode");
        bean.desc = object.getString("desc");
        return bean;
    }

    /**
     * @param inputStream 要转换的输入流
     * @return
     */
    private String stream2string(InputStream inputStream) {

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder builder = new StringBuilder();
        try {
            String line = reader.readLine();
            while (line != null) {
                builder.append(line);
                line = reader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return builder.toString();
    }

    /**
     * 开始动画
     */
    private void startAnimation() {
        mAnimationSet = new AnimationSet(false);

        RotateAnimation rotateAnimation = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, .5f, Animation.RELATIVE_TO_SELF, .5f);
        rotateAnimation.setDuration(2000);
        rotateAnimation.setFillAfter(true);
        mAnimationSet.addAnimation(rotateAnimation);

        AlphaAnimation alphaAnimation = new AlphaAnimation(.0f, 1.0f);
        alphaAnimation.setDuration(2000);
        alphaAnimation.setFillAfter(true);
        mAnimationSet.addAnimation(alphaAnimation);

        ScaleAnimation scaleAnimation = new ScaleAnimation(.0f, 1.0f, .0f, 1.0f, Animation.RELATIVE_TO_SELF, .5f, Animation.RELATIVE_TO_SELF, .5f);
        scaleAnimation.setDuration(2000);
        scaleAnimation.setFillAfter(true);
        mAnimationSet.addAnimation(scaleAnimation);


        mRl_splash.startAnimation(mAnimationSet);

    }

    private void initDate() {
        PackageManager packageManager = getPackageManager();
        //0代表获取当前应用的版本号，有很多信息可以获取通过PackageManager点出常量进行获取
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(getPackageName(), 0);
            mVersionCode = packageInfo.versionCode;
            String versionName = packageInfo.versionName;

            mTv_versionName.setText(versionName);
            //这里要注意一定要是字符串类型的但是直接这样写会警告，还是setText的问题
//            mTv_versionCode.setText(versionCode + "");
            mTv_versionCode.setText(getString(R.string.string, mVersionCode));

        } catch (PackageManager.NameNotFoundException e) {
            //can not reach
        }
    }

    private void initView() {
        setContentView(R.layout.activity_splash);

        mRl_splash = (RelativeLayout) findViewById(R.id.rl_splash);

        mTv_versionName = (TextView) findViewById(R.id.tv_splash_version_name);
        mTv_versionCode = (TextView) findViewById(R.id.tv_splash_version_code);
    }

    private class MyAnimationListener implements Animation.AnimationListener {

        @Override
        public void onAnimationStart(Animation animation) {

        }

        @Override
        public void onAnimationEnd(Animation animation) {

        }

        @Override
        public void onAnimationRepeat(Animation animation) {

        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        finish();
    }
}
