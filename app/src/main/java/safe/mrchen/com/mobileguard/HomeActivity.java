package safe.mrchen.com.mobileguard;

import android.app.Activity;
import android.os.Bundle;

/**
 * Created by ${chenyn} on 16/3/27.
 *
 * @desc :
 * @parame :
 * @return :
 */
public class HomeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initView();
    }

    private void initView() {
        setContentView(R.layout.activity_home);
    }
}
