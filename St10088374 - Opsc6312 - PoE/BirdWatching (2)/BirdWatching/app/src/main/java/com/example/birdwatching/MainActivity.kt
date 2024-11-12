package com.example.birdwatching

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.tasks.Task
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.sign

class MainActivity : AppCompatActivity() {

    lateinit var emailTextBox : TextInputEditText
    lateinit var passwordTextBox : TextInputEditText
    lateinit var loginBtn : Button
    lateinit var signUpTV: TextView

    var firebaseAuth : FirebaseAuth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        emailTextBox = findViewById(R.id.email_signin)
        passwordTextBox = findViewById(R.id.password_signin)
        loginBtn = findViewById(R.id.login_btn)
        signUpTV = findViewById(R.id.SignUpLink)

        signUpTV.setOnClickListener(View.OnClickListener { startActivity(Intent(this, RegisterPage::class.java)) })

        loginBtn.setOnClickListener(View.OnClickListener {

            val email = emailTextBox.text.toString()
            val password = passwordTextBox.text.toString()

            if(TextUtils.isEmpty(email)){
                Toast.makeText(this, "Enter email", Toast.LENGTH_SHORT).show()
                return@OnClickListener
            }

            if(TextUtils.isEmpty(password)){
                Toast.makeText(this, "Enter password", Toast.LENGTH_SHORT).show()
                return@OnClickListener
            }

            firebaseAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener {task ->
                if(task.isSuccessful){
                    Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, HomePage::class.java))
                }
                else{
                    Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
                }
            }

        })
    }
}