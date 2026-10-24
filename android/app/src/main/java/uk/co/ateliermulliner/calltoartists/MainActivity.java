package uk.co.ateliermulliner.calltoartists;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(CtaNativePlugin.class);
        super.onCreate(savedInstanceState);
    }
}
