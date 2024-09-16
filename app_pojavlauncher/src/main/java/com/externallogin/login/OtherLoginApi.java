package com.externallogin.login;

import android.util.Log;

import com.google.gson.Gson;

import net.kdt.pojavlaunch.value.MinecraftAccount;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OtherLoginApi {
    private static OkHttpClient client;
    private static final OtherLoginApi INSTANCE = new OtherLoginApi();
    private String baseUrl;

    private OtherLoginApi() {
        client = new OkHttpClient();
    }

    public static OtherLoginApi getINSTANCE() {
        return INSTANCE;
    }

    public void setBaseUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        this.baseUrl = baseUrl;
        System.out.println(this.baseUrl);
    }

    public void login(String userName, String password, Listener listener) throws IOException {
        if (Objects.isNull(baseUrl)) {
            listener.onFailed("BaseUrl is not set");
            return;
        }
        AuthRequest authRequest = new AuthRequest();
        authRequest.setUsername(userName);
        authRequest.setPassword(password);
        AuthRequest.Agent agent = new AuthRequest.Agent();
        agent.setName("Client");
        agent.setVersion(1.0);
        authRequest.setAgent(agent);
        authRequest.setRequestUser(true);
        authRequest.setClientToken(UUID.randomUUID().toString().toLowerCase(Locale.ROOT));
        String data = new Gson().toJson(authRequest);
        System.out.println(data);
        baseLogin(data, "/authserver/authenticate", listener);
    }

    public void refresh(MinecraftAccount account, boolean select, Listener listener) throws IOException {
        if (Objects.isNull(baseUrl)) {
            listener.onFailed("BaseUrl is not set");
            return;
        }
        Refresh refresh = new Refresh();
        refresh.setClientToken(account.clientToken);
        refresh.setAccessToken(account.accessToken);
        if (select) {
            Refresh.SelectedProfile selectedProfile = new Refresh.SelectedProfile();
            selectedProfile.setName(account.username);
            selectedProfile.setId(account.profileId);
            refresh.setSelectedProfile(selectedProfile);
        }
        String data = new Gson().toJson(refresh);
        System.out.println(data);
        baseLogin(data, "/authserver/refresh", listener);
    }

    private void baseLogin(String data, String url, Listener listener) throws IOException {
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), data);
        Request request = new Request.Builder()
                .url(baseUrl + url)
                .post(body)
                .build();
        Call call = client.newCall(request);
        callLogin(call, listener);
    }

    private void callLogin(Call call, Listener listener) throws IOException {
        Response response = call.execute();
        String res = response.body().string();
        System.out.println(res);
        if (response.code() == 200) {
            AuthResult result = new Gson().fromJson(res, AuthResult.class);
            listener.onSuccess(result);
        } else {
            listener.onFailed("error code：" + response.code() + "\n" + res);
        }
    }

    public String getServeInfo(String url) {
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();
            Call call = client.newCall(request);
            Response response = call.execute();
            String res = response.body().string();
            System.out.println(res);
            if (response.code() == 200) {
                return res;
            }
        } catch (Exception e) {
            Log.e("test", "" + e.toString());
        }
        return null;
    }

    public interface Listener {
        void onSuccess(AuthResult authResult);

        void onFailed(String error);
    }

}
