package com.android.nexcode.presenters.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.android.nexcode.R;
import com.android.nexcode.models.User;
import com.android.nexcode.repositories.firebase.UserRepository;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;

public class SettingsActivity extends AppCompatActivity {

    LinearLayout layout_edit_personal_data, layout_change_profile_picture, layout_delete_account;
    ImageView btn_back;
    SwitchCompat switch_notifications, switch_group_messaging;
    UserRepository userRepository;
    FirebaseDatabase database;
    FirebaseFirestore firestore;
    FirebaseAuth auth;
    LinearLayout groupMsgToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Initialize views
        layout_edit_personal_data = findViewById(R.id.layout_edit_personal_data);
        layout_change_profile_picture = findViewById(R.id.layout_change_profile_picture);
        layout_delete_account = findViewById(R.id.layout_delete_account);
        btn_back = findViewById(R.id.btn_back);
        switch_notifications = findViewById(R.id.switch_notifications);
        switch_group_messaging = findViewById(R.id.switch_group_messaging);
        groupMsgToggle = findViewById(R.id.groupMsgToggle);

        // Initialize Firebase components
        userRepository = new UserRepository(this);
        database = FirebaseDatabase.getInstance();
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // Load user data and set switch states
        userRepository.loadUserData(new UserRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                switch_notifications.setChecked(user.isNotification());
                if(user.getRole().equals("User")) {
                    groupMsgToggle.setVisibility(View.GONE);
                }
            }
            @Override
            public void onFailure(String message) {
                Toast.makeText(SettingsActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });

        database.getReference("group_chat/mode").get().addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        Boolean mode = snapshot.getValue(Boolean.class);
                        if (mode != null) {
                            switch_group_messaging.setChecked(mode);
                        }
                    }
                });
        // Back button click listener
        btn_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        // Group messaging switch
        switch_group_messaging.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                database.getReference("group_chat/mode").setValue(switch_group_messaging.isChecked());
            }
        });

        // Notifications switch
        switch_notifications.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                userRepository.updateNotificationPreference(switch_notifications.isChecked());
            }
        });

        // Edit personal data click listener
        layout_edit_personal_data.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(SettingsActivity.this, EditProfile.class));
            }
        });

        // Change profile picture click listener
        layout_change_profile_picture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(SettingsActivity.this, ChangeProfilePicture.class));
            }
        });

        // Delete account click listener
        layout_delete_account.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showDeleteAccountConfirmDialog();
            }
        });
    }

    private void showDeleteAccountConfirmDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete Account")
                .setMessage("Are you sure you want to delete your account? This action cannot be undone and all your data will be permanently deleted.")
                .setIcon(R.drawable.ic_delete)
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showReAuthenticationDialog();
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void showReAuthenticationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Confirm Your Password");
        builder.setMessage("For security reasons, please enter your password to confirm account deletion:");

        // Create password input field
        final EditText passwordInput = new EditText(this);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setHint("Enter your password");

        // Add some padding to the input field
        LinearLayout container = new LinearLayout(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(50, 20, 50, 20);
        passwordInput.setLayoutParams(params);
        container.addView(passwordInput);

        builder.setView(container);

        builder.setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String password = passwordInput.getText().toString().trim();
                if (!password.isEmpty()) {
                    reAuthenticateAndDelete(password);
                } else {
                    Toast.makeText(SettingsActivity.this, "Please enter your password", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void reAuthenticateAndDelete(String password) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null && currentUser.getEmail() != null) {
            // Create credential with email and password
            AuthCredential credential = EmailAuthProvider.getCredential(currentUser.getEmail(), password);

            // Re-authenticate user
            currentUser.reauthenticate(credential)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(Task<Void> task) {
                            if (task.isSuccessful()) {
                                // Re-authentication successful, now delete account
                                deleteUserAccount();
                            } else {
                                Toast.makeText(SettingsActivity.this,
                                        "Authentication failed. Please check your password and try again.",
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    });
        } else {
            Toast.makeText(this, "Unable to authenticate user", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteUserAccount() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();

            // First delete user data from Firestore
            firestore.collection("User").document(userId).delete()
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(Task<Void> task) {
                            if (task.isSuccessful()) {
                                // Then delete the user authentication account
                                currentUser.delete().addOnCompleteListener(new OnCompleteListener<Void>() {
                                    @Override
                                    public void onComplete(Task<Void> task) {
                                        if (task.isSuccessful()) {
                                            Toast.makeText(SettingsActivity.this, "Account deleted successfully", Toast.LENGTH_SHORT).show();
                                            // Navigate to login screen or main activity
                                            Intent intent = new Intent(SettingsActivity.this, Login.class);
                                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                            startActivity(intent);
                                            finish();
                                        } else {
                                            Toast.makeText(SettingsActivity.this, "Failed to delete account: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                                        }
                                    }
                                });
                            } else {
                                Toast.makeText(SettingsActivity.this, "Failed to delete user data: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                            }
                        }
                    });
        } else {
            Toast.makeText(this, "No user logged in", Toast.LENGTH_SHORT).show();
        }
    }
}