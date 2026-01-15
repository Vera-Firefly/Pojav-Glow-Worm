package net.kdt.pojavlaunch.firefly.fragments;

import static com.firefly.utils.ToastUtils.Toast;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.externallogin.login.AuthResult;
import com.externallogin.login.OtherLoginApi;
import com.externallogin.login.Servers;
import com.firefly.ui.dialog.CustomDialog;
import com.google.gson.Gson;
import com.kdt.mcgui.MineButton;
import com.kdt.mcgui.MineEditText;

import net.kdt.pojavlaunch.firefly.PojavApplication;
import net.kdt.pojavlaunch.firefly.R;
import net.kdt.pojavlaunch.firefly.Tools;
import net.kdt.pojavlaunch.firefly.extra.ExtraConstants;
import net.kdt.pojavlaunch.firefly.extra.ExtraCore;
import net.kdt.pojavlaunch.firefly.value.MinecraftAccount;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OtherLoginFragment extends Fragment {
    public static final String TAG = "OtherLoginFragment";
    private ProgressDialog progressDialog;
    private Spinner serverSpinner;
    private MineEditText userEditText;
    private MineEditText passEditText;
    private MineButton loginButton;
    private TextView register;
    private ImageButton addServer;
    private File serversFile;
    private Servers servers;
    private List<String> serverList;
    public String currentBaseUrl;
    private String currentRegisterUrl;
    private ArrayAdapter<String> serverSpinnerAdapter;

    private String[] serverType;
    private String[] serverTypeHint;

    public OtherLoginFragment() {
        super(R.layout.fragment_other_login);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        serverType = new String[] {
                getString(R.string.other_login_external),
                getString(R.string.other_login_pass)
        };

        serverTypeHint = new String[] {
                getString(R.string.other_login_address),
                getString(R.string.other_login_setid)
        };

        serversFile = new File(Tools.DIR_GAME_HOME, "servers.json");
        progressDialog = new ProgressDialog(requireContext());
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);

        serverSpinner = view.findViewById(R.id.server_spinner);
        userEditText = view.findViewById(R.id.login_edit_email);
        passEditText = view.findViewById(R.id.login_edit_password);
        loginButton = view.findViewById(R.id.login_button);
        register = view.findViewById(R.id.register);
        addServer = view.findViewById(R.id.add_server);

        refreshServer();
        serverSpinner.setAdapter(serverSpinnerAdapter);
        serverSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (!Objects.isNull(servers)) {
                    for (Servers.Server server : servers.getServer()) {
                        if (server.getServerName().equals(serverList.get(i))) {
                            currentBaseUrl = server.getBaseUrl();
                            currentRegisterUrl = server.getRegister();
                            Log.e("test", "currentRegisterUrl:" + currentRegisterUrl);
                        }
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        addServer.setOnClickListener(v -> showServerTypeDialog());

        register.setOnClickListener(v -> {
            if (!Objects.isNull(currentRegisterUrl)) {
                Intent intent = new Intent();
                intent.setAction("android.intent.action.VIEW");
                Uri url = Uri.parse(currentRegisterUrl);
                intent.setData(url);
                startActivity(intent);
            }
        });

        loginButton.setOnClickListener(v -> handleLogin());
    }

    private void showServerTypeDialog() {
        new CustomDialog.Builder(requireContext())
                .setTitle(getString(R.string.other_login_aut))
                .setCancelable(false)
                .setItems(serverType, (selectedSource, i) -> showServerInputDialog(selectedSource))
                .setConfirmListener(R.string.other_login_cancel, customView -> true)
                .setDraggable(true)
                .build()
                .show();
    }

    private void showServerInputDialog(String selectedSource) {
        EditText editText = createServerInputEditText(selectedSource);

        new CustomDialog.Builder(requireContext())
                .setTitle(selectedSource)
                .setCustomView(editText)
                .setCancelable(false)
                .setConfirmListener(R.string.other_login_confirm, customView -> {
                    handleServerInput(selectedSource, editText.getText().toString());
                    return true;
                })
                .setCancelListener(R.string.other_login_cancel, customView -> true)
                .setDraggable(true)
                .build()
                .show();
    }

    private EditText createServerInputEditText(String selectedSource) {
        EditText editText = new EditText(requireContext());
        editText.setMaxLines(1);
        editText.setInputType(InputType.TYPE_CLASS_TEXT);

        String hintResId = selectedSource.equals(serverType[0]) ? serverTypeHint[0] : serverTypeHint[1];
        editText.setHint(hintResId);

        return editText;
    }

    private void handleServerInput(String selectedSource, String inputText) {
        PojavApplication.sExecutorService.execute(() -> {
            String serverUrl = buildServerUrl(selectedSource, inputText);
            String data = OtherLoginApi.getINSTANCE().getServeInfo(serverUrl);

            requireActivity().runOnUiThread(() -> {
                if (data != null) {
                    processServerData(selectedSource, serverUrl, data);
                } else {
                    Toast(getContext(), R.string.other_login_server_connect_error);
                }
            });
        });
    }

    private String buildServerUrl(String selectedSource, String inputText) {
        if (selectedSource.equals(serverType[0])) {
            String url = inputText.trim();
            while (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }

            if (!url.matches("^https?://.*")) {
                url = "https://" + url;
            }

            if (!url.matches(".*/api/(?:yggdrasil|authlib-injector)$")) {
                url = url.replaceAll("/api/(?:yggdrasil|authlib-injector).*$", "");
                url += "/api/yggdrasil";
            }
            return url;
        } else {
            return "https://auth.mc-user.com:233/" + inputText;
        }
    }

    private void processServerData(String selectedSource, String serverUrl, String data) {
        progressDialog.show();

        try {
            Servers.Server server = createServerFromData(selectedSource, serverUrl, data);
            addServerToConfig(server);

            currentBaseUrl = server.getBaseUrl();
            currentRegisterUrl = server.getRegister();
            refreshServer();

        } catch (Exception e) {
            Log.e("OtherLogin: ", Log.getStackTraceString(e));
        } finally {
            progressDialog.dismiss();
        }
    }

    private Servers.Server createServerFromData(String selectedSource, String serverUrl, String data) throws JSONException {
        Servers.Server server = new Servers.Server();
        JSONObject jsonObject = new JSONObject(data);
        JSONObject meta = jsonObject.optJSONObject("meta");

        server.setServerName(meta.optString("serverName"));
        server.setBaseUrl(serverUrl);

        if (selectedSource.equals(serverType[0])) {
            JSONObject links = meta.optJSONObject("links");
            server.setRegister(links.optString("register"));
        } else {
            server.setBaseUrl("https://auth.mc-user.com:233/" + serverUrl);
            server.setRegister("https://login.mc-user.com:233/" + serverUrl + "/loginreg");
        }

        return server;
    }

    private void addServerToConfig(Servers.Server server) throws IOException {
        if (Objects.isNull(servers)) {
            servers = new Servers();
            servers.setServer(new ArrayList<>());
        }

        servers.getServer().add(server);
        String json = Tools.GLOBAL_GSON.toJson(servers, Servers.class);
        Tools.write(serversFile.getAbsolutePath(), json);
    }

    private void handleLogin() {
        PojavApplication.sExecutorService.execute(this::performLogin);
    }

    private void performLogin() {
        String user = userEditText.getText().toString();
        String pass = passEditText.getText().toString();
        String baseUrl = currentBaseUrl;

        if (!checkAccountInformation(user, pass)) return;

        if (baseUrl == null || baseUrl.isEmpty()) {
            return;
        }

        requireActivity().runOnUiThread(() -> progressDialog.show());

        try {
            OtherLoginApi.getINSTANCE().setBaseUrl(baseUrl);
            OtherLoginApi.getINSTANCE().login(user, pass, new OtherLoginApi.Listener() {
                @Override
                public void onSuccess(AuthResult authResult) {
                    handleLoginSuccess(authResult, user);
                }

                @Override
                public void onFailed(String error) {
                    handleLoginError(error);
                }
            });
        } catch (IOException e) {
            handleLoginException(e);
        }
    }

    private void handleLoginSuccess(AuthResult authResult, String username) {
        requireActivity().runOnUiThread(() -> {
            progressDialog.dismiss();

            if (authResult.getSelectedProfile() != null) {
                handleSingleProfileLogin(authResult, username);
            } else {
                handleMultipleProfileSelection(authResult, username);
            }
        });
    }

    private void handleSingleProfileLogin(AuthResult authResult, String username) {
        MinecraftAccount account = createAccountFromAuthResult(authResult, username);
        account.username = authResult.getSelectedProfile().getName();
        account.profileId = authResult.getSelectedProfile().getId();

        refresh(account);
    }

    private void handleMultipleProfileSelection(AuthResult authResult, String username) {
        List<String> profileNames = extractProfileNames(authResult.getAvailableProfiles());
        String[] items = profileNames.toArray(new String[0]);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.other_login_role)
                .setItems(items, (d, i) -> {
                    handleProfileSelection(authResult, username, items[i]);
                })
                .setNegativeButton(R.string.other_login_cancel, null)
                .create()
            .show();
    }

    private void handleProfileSelection(AuthResult authResult, String username, String selectedProfileName) {
        for (AuthResult.AvailableProfiles profile : authResult.getAvailableProfiles()) {
            if (profile.getName().equals(selectedProfileName)) {
                MinecraftAccount account = createAccountFromAuthResult(authResult, username);
                account.profileId = profile.getId();
                account.username = profile.getName();
                refresh(account);
                break;
            }
        }
    }

    private List<String> extractProfileNames(List<AuthResult.AvailableProfiles> profiles) {
        List<String> names = new ArrayList<>();
        for (AuthResult.AvailableProfiles profile : profiles) {
            names.add(profile.getName());
        }
        return names;
    }

    private MinecraftAccount createAccountFromAuthResult(AuthResult authResult, String username) {
        MinecraftAccount account = new MinecraftAccount();
        account.accessToken = authResult.getAccessToken();
        account.clientToken = authResult.getClientToken();
        account.baseUrl = currentBaseUrl;
        account.account = username;
        return account;
    }

    private void handleLoginError(String error) {
        requireActivity().runOnUiThread(() -> {
            progressDialog.dismiss();
            showErrorDialog(error);
        });
    }

    private void showErrorDialog(String error) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.other_login_warning)
                .setMessage(error)
                .setPositiveButton(R.string.other_login_confirm, null)
                .create()
                .show();
    }

    private void handleLoginException(IOException e) {
        requireActivity().runOnUiThread(() -> progressDialog.dismiss());
        Log.e("login", "Login exception", e);
    }

    private void refresh(MinecraftAccount account) {
        PojavApplication.sExecutorService.execute(() -> {
            try {
                OtherLoginApi.getINSTANCE().refresh(account, true, new OtherLoginApi.Listener() {
                    @Override
                    public void onSuccess(AuthResult authResult) {
                        account.accessToken = authResult.getAccessToken();
                        ((Activity) getContext()).runOnUiThread(() -> {
                            progressDialog.dismiss();
                            ExtraCore.setValue(ExtraConstants.OTHER_LOGIN_TODO, account);
                            Tools.swapFragment(requireActivity(), MainMenuFragment.class, MainMenuFragment.TAG, null);
                        });
                    }

                    @Override
                    public void onFailed(String error) {
                        requireActivity().runOnUiThread(() -> {
                            progressDialog.dismiss();
                            new AlertDialog.Builder(requireContext())
                                    .setTitle(R.string.other_login_warning)
                                    .setTitle("An error occurred while logging in：\n" + error)
                                    .setPositiveButton(R.string.other_login_confirm, null)
                                    .create()
                                    .show();
                        });
                    }
                });
            } catch (IOException e) {
                requireActivity().runOnUiThread(() -> progressDialog.dismiss());
                Log.e("login", e.toString());
            }
        });
    }

    public void refreshServer() {
        if (Objects.isNull(serverList)) {
            serverList = new ArrayList<>();
        } else {
            serverList.clear();
        }
        if (serversFile.exists()) {
            try {
                servers = new Gson().fromJson(Tools.read(serversFile.getAbsolutePath()), Servers.class);
                currentBaseUrl = servers.getServer().get(0).getBaseUrl();
                for (Servers.Server server : servers.getServer()) {
                    serverList.add(server.getServerName());
                }
            } catch (IOException e) {

            }
        }
        if (Objects.isNull(servers)) {
            serverList.add(getString(R.string.other_login_server_error));
        }
        if (Objects.isNull(serverSpinnerAdapter)) {
            serverSpinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, serverList);
        } else {
            serverSpinnerAdapter.notifyDataSetChanged();
        }

    }

    private boolean checkAccountInformation(String user, String pass) {
        boolean userTextEmpty = user.isEmpty();
        boolean passTextEmpty = pass.isEmpty();

        if (userTextEmpty || passTextEmpty) {
            if (userTextEmpty) {
                // User or Mail cannot be empty
            }
            if (passTextEmpty) {
                // Password cannot be empty
            }
            return false;
        } else {
            return true;
        }
    }

}
