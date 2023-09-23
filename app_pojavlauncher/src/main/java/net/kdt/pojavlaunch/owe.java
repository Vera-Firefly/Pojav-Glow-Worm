package net.kdt.pojavlaunch;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

public class owe extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_owe);
      Toast.makeText(this, "欢迎使用Beta版!!!", Toast.LENGTH_SHORT).show();
    }
}