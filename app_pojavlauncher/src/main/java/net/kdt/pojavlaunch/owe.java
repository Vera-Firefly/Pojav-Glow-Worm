package net.kdt.pojavlaunch;

import android.os.*;
import androidx.appcompat.app.*;
import android.content.Context;

import android.widget.Toast;

public abstract class owe extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_owe);
      Toast.makeText((Context)owe.this, (CharSequence)"#欢迎使用Beta版!!!", Toast.LENGTH_LONG).show();
    }
}
