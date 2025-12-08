package com.example.react_io.services;

import android.util.Log;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.example.react_io.models.User;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;

public class AuthService {
    private static final String TAG = "AuthService";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    public AuthService() {
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    public interface AuthCallback {
        void onSuccess(FirebaseUser user);

        void onFailure(String error);
    }

    public void registerUser(String email, String password, String username, AuthCallback callback) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        if (firebaseUser != null) {
                            // 1. ENVIAR EMAIL DE VERIFICACIÓN
                            firebaseUser.sendEmailVerification()
                                    .addOnCompleteListener(emailTask -> {
                                        if (emailTask.isSuccessful()) {
                                            Log.d(TAG, "Email de verificación enviado.");
                                        }
                                    });

                            // 2. Guardar datos Firestore
                            User user = new User(firebaseUser.getUid(), email, username);
                            db.collection("users").document(firebaseUser.getUid())
                                    .set(user)
                                    .addOnSuccessListener(aVoid -> callback.onSuccess(firebaseUser))
                                    .addOnFailureListener(e -> callback.onFailure("Error al crear perfil"));
                        }
                    } else {
                        callback.onFailure(task.getException().getMessage());
                    }
                });
    }

    public void loginUser(String email, String password, AuthCallback callback) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        callback.onSuccess(user);
                    } else {
                        Log.w(TAG, "signInWithEmail:failure", task.getException());
                        callback.onFailure(getFriendlyErrorMessage(task.getException()));
                    }
                });
    }

    private void checkAndSaveGoogleUser(FirebaseUser firebaseUser) {
        // Verificar si el usuario existe en Firestore, si no, crearlo
        db.collection("users").document(firebaseUser.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        User user = new User(firebaseUser.getUid(), firebaseUser.getEmail(),
                                firebaseUser.getDisplayName());
                        db.collection("users").document(firebaseUser.getUid()).set(user);
                    }
                });
    }

    public void firebaseAuthWithGoogle(String idToken, AuthCallback callback) {
        com.google.firebase.auth.AuthCredential credential = com.google.firebase.auth.GoogleAuthProvider
                .getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        checkAndSaveGoogleUser(user);
                        callback.onSuccess(user);
                    } else {
                        Log.w(TAG, "signInWithGoogle:failure", task.getException());
                        callback.onFailure(getFriendlyErrorMessage(task.getException()));
                    }
                });
    }

    private String getFriendlyErrorMessage(Exception e) {
        if (e instanceof FirebaseAuthInvalidUserException) {
            return "El usuario no existe o ha sido deshabilitado.";
        } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
            return "Credenciales incorrectas (email o contraseña).";
        } else if (e instanceof FirebaseAuthUserCollisionException) {
            return "El email ya está registrado.";
        } else if (e instanceof FirebaseNetworkException) {
            return "Error de conexión. Revisá tu internet.";
        }

        return "Error: " + e.getMessage(); // default!
    }

    public void logout() {
        mAuth.signOut();
    }

    public FirebaseUser getCurrentUser() {
        return mAuth.getCurrentUser();
    }

    public boolean isUserLoggedIn() {
        return mAuth.getCurrentUser() != null;
    }
}