package com.android.nexcode.presenters.activities;

import static android.content.ContentValues.TAG;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.nexcode.R;
import com.android.nexcode.models.User;
import com.android.nexcode.repositories.firebase.UserRepository;
import com.android.nexcode.utils.UserAuthenticationUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class Login extends AppCompatActivity {

    // UI Elements
    private TextInputLayout emailInputLayout, passwordInputLayout;
    private TextInputEditText emailInput, passwordInput;
    private MaterialButton loginButton;
    private TextView forgotPassword, signUp, contactUs;
    private MaterialCardView googleCard, facebookCard, appleCard, verificationBanner;
    private TextView verificationMessage;
    private ImageButton closeBanner;
    private ProgressBar loginProgress, googleProgress;
    private ImageView googleIcon;

    // Firebase
    UserRepository userRepository;
    UserAuthenticationUtils userAuthenticationUtils;
    User user;
    private FirebaseAuth mAuth;

    // Loading state
    private boolean isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize Firebase
        userRepository = new UserRepository(this);
        userAuthenticationUtils = new UserAuthenticationUtils(this);
        mAuth = FirebaseAuth.getInstance();

        // Initialize UI components
        initializeViews();
        setupClickListeners();

        // Check if coming from signup with verification message
        handleSignupExtras();
    }

    private void initializeViews() {
        // Find all views
        emailInputLayout = findViewById(R.id.email_input_layout);
        passwordInputLayout = findViewById(R.id.password_input_layout);
        emailInput = findViewById(R.id.email_input);
        passwordInput = findViewById(R.id.password_input);
        loginButton = findViewById(R.id.login_button);
        forgotPassword = findViewById(R.id.forget_password);
        signUp = findViewById(R.id.sign_up);
        contactUs = findViewById(R.id.contact_us);
        googleCard = findViewById(R.id.google_card);
        verificationBanner = findViewById(R.id.verification_banner);
        verificationMessage = findViewById(R.id.verification_message);
        closeBanner = findViewById(R.id.close_banner);
        loginProgress = findViewById(R.id.login_progress);
        googleProgress = findViewById(R.id.google_progress);
        googleIcon = findViewById(R.id.google_icon);
    }

    private void handleSignupExtras() {
        Intent intent = getIntent();
        if (intent.getBooleanExtra("showVerificationMessage", false)) {
            String email = intent.getStringExtra("email");
            if (email != null) {
                emailInput.setText(email);
                showVerificationBanner("Please check your email and verify your account before logging in.");
            }
        }
    }

    private void showVerificationBanner(String message) {
        if (verificationBanner != null && verificationMessage != null) {
            verificationMessage.setText(message);
            verificationBanner.setVisibility(View.VISIBLE);
        }
    }

    private void hideVerificationBanner() {
        if (verificationBanner != null) {
            verificationBanner.setVisibility(View.GONE);
        }
    }

    private void setupClickListeners() {
        // Back button
        findViewById(R.id.back_button).setOnClickListener(v -> finish());

        // Login button
        loginButton.setOnClickListener(v -> {
            if (!isLoading) {
                validateAndLogin();
            }
        });

        // Forgot password
        forgotPassword.setOnClickListener(v -> {
            Intent intent = new Intent(Login.this, ForgotPassword.class);
            startActivity(intent);
        });

        // Sign up
        signUp.setOnClickListener(v -> {
            Intent intent = new Intent(Login.this, SignUp.class);
            startActivity(intent);
        });

        // Contact us
        contactUs.setOnClickListener(v -> {
            Toast.makeText(this, "Contact support at support@nexcode.com", Toast.LENGTH_LONG).show();
        });

        // Close banner button
        if (closeBanner != null) {
            closeBanner.setOnClickListener(v -> hideVerificationBanner());
        }

        // Google Sign-In button
        googleCard.setOnClickListener(v -> {
            if (!isLoading) {
                signInWithGoogle();
            }
        });
    }

    private void validateAndLogin() {
        // Clear previous errors
        emailInputLayout.setError(null);
        passwordInputLayout.setError(null);

        // Get input values
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        // Validate email
        if (email.isEmpty()) {
            emailInputLayout.setError("Email is required");
            return;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInputLayout.setError("Please enter a valid email address");
            return;
        }

        // Validate password
        if (password.isEmpty()) {
            passwordInputLayout.setError("Password is required");
            return;
        } else if (password.length() < 8) {
            passwordInputLayout.setError("Password must be at least 8 characters");
            return;
        }

        // All validations passed, proceed with login
        setLoading(true);

        userAuthenticationUtils.login(email, password, new UserAuthenticationUtils.Callback() {
            @Override
            public void onSuccess() {
                navigateToMain();
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(Login.this, message, Toast.LENGTH_LONG).show();
            }
        });

    }

    private void signInWithGoogle() {
        setLoading(true);
        setGoogleLoading(true);

        userRepository.signInWithGoogle(new UserRepository.GoogleSignInCallback() {
            @Override
            public void onSuccess(User user) {
                // Google Sign-In successful
                setLoading(false);
                setGoogleLoading(false);

                Login.this.user = user;

                // Hide verification banner if visible
                hideVerificationBanner();

                // Navigate to main activity
                navigateToMain();
            }

            @Override
            public void onFailure(String message) {
                // Google Sign-In failed
                setLoading(false);
                setGoogleLoading(false);

                Log.e(TAG, "Google Sign-In failed: " + message);
                Toast.makeText(Login.this, "Google Sign-In failed: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void navigateToMain() {
        Intent intent = new Intent(Login.this, Main.class);
        startActivity(intent);
        finish();
    }

    private void resendVerificationEmail(FirebaseUser user) {
        user.sendEmailVerification()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(Login.this,
                                "Verification email sent to " + user.getEmail(),
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(Login.this,
                                "Failed to send verification email. Please try again.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setLoading(boolean loading) {
        isLoading = loading;

        // Update UI to show loading state
        if (loading) {
            loginButton.setText("Logging in...");
            loginButton.setEnabled(false);
            emailInput.setEnabled(false);
            passwordInput.setEnabled(false);
            googleCard.setEnabled(false);

            // Show progress indicators if available
            if (loginProgress != null) {
                loginProgress.setVisibility(View.VISIBLE);
            }
        } else {
            loginButton.setText("Login");
            loginButton.setEnabled(true);
            emailInput.setEnabled(true);
            passwordInput.setEnabled(true);
            googleCard.setEnabled(true);

            // Hide progress indicators if available
            if (loginProgress != null) {
                loginProgress.setVisibility(View.GONE);
            }
        }
    }

    private void setGoogleLoading(boolean loading) {
        if (googleProgress != null && googleIcon != null) {
            if (loading) {
                googleIcon.setVisibility(View.GONE);
                googleProgress.setVisibility(View.VISIBLE);
                googleCard.setEnabled(false);
            } else {
                googleIcon.setVisibility(View.VISIBLE);
                googleProgress.setVisibility(View.GONE);
                googleCard.setEnabled(true);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check if user is already signed in and verified
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // For Google users, they don't need email verification
            // For email/password users, check if email is verified
            if (currentUser.getProviderData().size() > 1 &&
                    currentUser.getProviderData().get(1).getProviderId().equals("google.com")) {
                // User signed in with Google, navigate to main
                navigateToMain();
            } else if (currentUser.isEmailVerified()) {
                // User signed in with email/password and is verified
                navigateToMain();
            }
            // If user is signed in but not verified (email/password), stay on login screen
        }
    }
}