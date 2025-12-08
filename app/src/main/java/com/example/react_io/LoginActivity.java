package com.example.react_io;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.react_io.services.AuthService;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseUser;
import androidx.credentials.CredentialManager;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import java.util.regex.Pattern;

public class LoginActivity extends AppCompatActivity {
    private EditText etEmail, etPassword, etUsername;
    private Button btnLogin, btnRegister;
    private AuthService authService;
    private boolean isRegisterMode = false;

    private TextInputLayout layoutUsername;
    private com.google.android.material.button.MaterialButton btnGoogleSignIn;

    private CredentialManager credentialManager;

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[0-9])(?=.*[A-Z]).{6,}$");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authService = new AuthService();

        // Verificar si el usuario ya está logeado
        if (authService.isUserLoggedIn()) {
            startMainActivity();
            return;
        }

        initViews();
        setupGoogleSignIn();
        setupClickListeners();
    }

    private void initViews() {
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etUsername = findViewById(R.id.etUsername);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);

        layoutUsername = findViewById(R.id.layoutUsername);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);

        layoutUsername.setVisibility(View.GONE);
    }

    private void setupGoogleSignIn() {
        credentialManager = CredentialManager.create(this);
    }

    private void setupClickListeners() {
        btnLogin.setOnClickListener(v -> {
            if (isRegisterMode) {
                registerUser();
            } else {
                loginUser();
            }
        });

        btnRegister.setOnClickListener(v -> toggleMode());

        btnGoogleSignIn.setOnClickListener(v -> signInWithGoogle());
    }

    private void signInWithGoogle() {
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(getString(R.string.default_web_client_id))
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        credentialManager.getCredentialAsync(
                this,
                request,
                new android.os.CancellationSignal(),
                getMainExecutor(),
                new androidx.credentials.CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        handleSignIn(result);
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        android.util.Log.e("LoginActivity", "Credential Manager error", e);
                        Toast.makeText(LoginActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void handleSignIn(GetCredentialResponse result) {
        androidx.credentials.Credential credential = result.getCredential();
        if (credential instanceof androidx.credentials.CustomCredential) {
            androidx.credentials.CustomCredential customCredential = (androidx.credentials.CustomCredential) credential;
            if (customCredential.getType().equals(GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)) {
                GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential
                        .createFrom(customCredential.getData());
                firebaseAuthWithGoogle(googleIdTokenCredential.getIdToken());
            } else {
                android.util.Log.e("LoginActivity", "Unexpected credential type: " + customCredential.getType());
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        authService.firebaseAuthWithGoogle(idToken, new AuthService.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                Toast.makeText(LoginActivity.this, "Google Sign-In exitoso", Toast.LENGTH_SHORT).show();
                startMainActivity();
            }

            @Override
            public void onFailure(String error) {
                Toast.makeText(LoginActivity.this, "Error autenticando con Google: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void toggleMode() {
        isRegisterMode = !isRegisterMode;
        if (isRegisterMode) {
            layoutUsername.setVisibility(View.VISIBLE); // Correcto
            btnLogin.setText("Registrarse");
            btnRegister.setText("Ya tienes cuenta? Inicia sesión");
        } else {
            layoutUsername.setVisibility(View.GONE);
            btnLogin.setText("Iniciar Sesión");
            btnRegister.setText("No tienes cuenta? Regístrate");
        }
    }

    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Por favor completá todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        authService.loginUser(email, password, new AuthService.AuthCallback() {
            @Override
            public void onSuccess(com.google.firebase.auth.FirebaseUser user) {
                Toast.makeText(LoginActivity.this, "Bienvenido!", Toast.LENGTH_SHORT).show();
                startMainActivity();
            }

            @Override
            public void onFailure(String error) {
                Toast.makeText(LoginActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void registerUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String username = etUsername.getText().toString().trim();

        // Validaciones básicas
        if (email.isEmpty() || password.isEmpty() || username.isEmpty()) {
            Toast.makeText(this, "Completá todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

        // 2. VALIDAR LA CONTRASEÑA
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            etPassword.setError("La contraseña debe tener al menos 6 caracteres, 1 número y 1 mayúscula");
            etPassword.requestFocus();
            return;
        }

        // Si pasa, llamamos al servicio
        authService.registerUser(email, password, username, new AuthService.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                Toast.makeText(LoginActivity.this, "Registro exitoso. Verificá tu email.", Toast.LENGTH_LONG).show();
                startMainActivity();
            }

            @Override
            public void onFailure(String error) {
                Toast.makeText(LoginActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}