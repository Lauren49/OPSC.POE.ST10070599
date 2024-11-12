package com.example.birdwatching

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth

class RegisterPage : AppCompatActivity() {

    lateinit var emailTextBox : TextInputEditText
    lateinit var passwordTextBox : TextInputEditText
    lateinit var signUpBtn : Button
    lateinit var signInTV: TextView

    var firebaseAuth : FirebaseAuth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_page)

        emailTextBox = findViewById(R.id.email_signup)
        passwordTextBox = findViewById(R.id.password_signup)
        signUpBtn = findViewById(R.id.sign_up)
        signInTV = findViewById(R.id.sign_in_link)

        signInTV.setOnClickListener(View.OnClickListener { startActivity(Intent(this, MainActivity::class.java)) })

        signUpBtn.setOnClickListener(View.OnClickListener {

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

            firebaseAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener {task->
                if(task.isSuccessful){
                    Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                }
                else{
                    Toast.makeText(this, "Registration failed", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
}