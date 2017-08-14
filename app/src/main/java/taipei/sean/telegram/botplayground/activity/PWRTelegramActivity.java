package taipei.sean.telegram.botplayground.activity;

import android.os.Bundle;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;

import taipei.sean.telegram.botplayground.InstantComplete;
import taipei.sean.telegram.botplayground.PWRTelegramAPI;
import taipei.sean.telegram.botplayground.R;
import taipei.sean.telegram.botplayground.SeanDBHelper;
import taipei.sean.telegram.botplayground.adapter.ApiCallerAdapter;

public class PWRTelegramActivity extends AppCompatActivity {
    final private int _dbVer = 2;
    private SeanDBHelper db;
    private String _token;
    private PWRTelegramAPI _api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pwrtelegram);

        try {
            Bundle bundle = getIntent().getExtras();
            _token = bundle.getString("token");
        } catch (NullPointerException e) {
            Log.e("caller", "bundle error", e);
            finish();
        }

        db = new SeanDBHelper(this, "data.db", null, _dbVer);

        _api = new PWRTelegramAPI(this, _token);

        final InstantComplete methodView = (InstantComplete) findViewById(R.id.pwrtelegram_method);
        final RecyclerView inputList = (RecyclerView) findViewById(R.id.pwrtelegram_inputs);
        final Button submitButton = (Button) findViewById(R.id.pwrtelegram_submit);
        final TextView resultView = (TextView) findViewById(R.id.pwrtelegram_result);


        final ArrayList<String> botApiMethodsList = new ArrayList<String>() {};
        final JSONObject pApiJson = loadPMethods();
        final JSONObject apiJson = loadMethods();


        try {
            JSONObject apiMethods = (JSONObject) pApiJson.get("methods");
            Iterator<String> temp = apiMethods.keys();
            while (temp.hasNext()) {
                String key = temp.next();
                botApiMethodsList.add(key);
            }
        } catch (JSONException e) {
            Log.e("caller", "parse p", e);
        }
        try {
            JSONObject apiMethods = (JSONObject) apiJson.get("methods");
            Iterator<String> temp = apiMethods.keys();
            while (temp.hasNext()) {
                String key = temp.next();
                botApiMethodsList.add(key);
            }
        } catch (JSONException e) {
            Log.e("caller", "parse", e);
        }

        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, botApiMethodsList);
        methodView.setAdapter(adapter);

        methodView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void afterTextChanged(Editable editable) {
                JSONObject paramData;
                String method = editable.toString();
                try {
                    JSONObject apiMethods = (JSONObject) apiJson.get("methods");
                    JSONObject pApiMethods = (JSONObject) pApiJson.get("methods");
                    if (pApiMethods.has(method)) {
                        JSONObject methodData = (JSONObject) pApiMethods.get(method);
                        paramData = (JSONObject) methodData.get("params");
                    } else if (apiMethods.has(method)) {
                        JSONObject methodData = (JSONObject) apiMethods.get(method);
                        paramData = (JSONObject) methodData.get("params");
                    } else {
                        methodView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
                        ViewGroup.LayoutParams layoutParams = inputList.getLayoutParams();
                        layoutParams.height = 0;
                        inputList.setLayoutParams(layoutParams);
                        return;
                    }
                } catch (JSONException e) {
                    Log.e("caller", "json", e);
                    return;
                }

                int paramCount = paramData.length();
                ViewGroup.LayoutParams layoutParams = inputList.getLayoutParams();
                if (paramCount <= 4)
                    layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                else
                    layoutParams.height = 600;   // 4 params
                inputList.setLayoutParams(layoutParams);

                ApiCallerAdapter apiCallerAdapter = new ApiCallerAdapter(getApplicationContext());

                try {
                    Iterator<String> temp = paramData.keys();
                    while (temp.hasNext()) {
                        String key = temp.next();
                        JSONObject value = (JSONObject) paramData.get(key);
                        apiCallerAdapter.addData(key, value);
                    }
                } catch (JSONException e) {
                    Log.e("caller", "parse", e);
                }

                inputList.setAdapter(apiCallerAdapter);
                inputList.setLayoutManager(new StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL));
                inputList.setItemViewCacheSize(paramData.length());
            }
        });

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                submit();
            }
        });

        submit();   // default enableGetMTProtoUpdates
    }

    private void submit() {
        final InstantComplete methodView = (InstantComplete) findViewById(R.id.pwrtelegram_method);
        final RecyclerView inputList = (RecyclerView) findViewById(R.id.pwrtelegram_inputs);
        final TextView resultView = (TextView) findViewById(R.id.pwrtelegram_result);

        String method = methodView.getText().toString();

        JSONObject jsonObject = new JSONObject();

        final RecyclerView.Adapter inputAdapter = inputList.getAdapter();
        if (null == inputAdapter) {
            _api.callApi(method, resultView, jsonObject);
            return;
        }

        final int inputCount = inputAdapter.getItemCount();
        for (int i=0; i<inputCount; i++) {
            RecyclerView.ViewHolder viewHolder = inputList.findViewHolderForAdapterPosition(i);
            if (null == viewHolder)
                continue;
            TextInputLayout textInputLayout = (TextInputLayout) viewHolder.itemView;
            InstantComplete textInputEditText = (InstantComplete) textInputLayout.getEditText();
            if (null == textInputEditText)
                continue;
            CharSequence hint = textInputLayout.getHint();
            if (null == hint)
                continue;
            String name = hint.toString();
            CharSequence valueChar = textInputEditText.getText();
            if (null == valueChar)
                continue;
            String value = valueChar.toString();

            if (Objects.equals(value, ""))
                continue;

            try {
                jsonObject.put(name, value);
                db.insertFav(name, value, method);
            } catch (JSONException e) {
                Log.e("caller", "json", e);
            }
        }

        _api.callApi(method, resultView, jsonObject);
    }

    public JSONObject loadMethods() {
        String jsonStr;
        JSONObject json;
        try {
            InputStream is = getAssets().open("api-methods.json");

            int size = is.available();
            byte[] buffer = new byte[size];

            if (is.read(buffer) < 0)
                return null;

            is.close();
            jsonStr = new String(buffer, "UTF-8");
        } catch (IOException e) {
            Log.e("caller", "get", e);
            return null;
        }
        try {
            json = new JSONObject(jsonStr);
        } catch (JSONException e) {
            Log.e("caller", "parse", e);
            return null;
        }
        return json;
    }

    public JSONObject loadPMethods() {
        String jsonStr;
        JSONObject json;
        try {
            InputStream is = getAssets().open("pwrtelegram-methods.json");

            int size = is.available();
            byte[] buffer = new byte[size];

            if (is.read(buffer) < 0)
                return null;

            is.close();
            jsonStr = new String(buffer, "UTF-8");
        } catch (IOException e) {
            Log.e("caller", "get", e);
            return null;
        }
        try {
            json = new JSONObject(jsonStr);
        } catch (JSONException e) {
            Log.e("caller", "parse", e);
            return null;
        }
        return json;
    }
}
