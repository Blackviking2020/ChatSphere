package com.example.chatsphere.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.chatsphere.R;
import com.example.chatsphere.databinding.ActivitySignInBinding;
import com.example.chatsphere.databinding.ActivitySignUpBinding;
import com.example.chatsphere.utilities.Constants;
import com.example.chatsphere.utilities.PreferenceManager;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Objects;
import java.util.regex.Pattern;

public class SignUpActivity extends AppCompatActivity {

    private ActivitySignUpBinding binding;
    private String encodedImage;
    private PreferenceManager preferenceManager;

    @Override
    //初始化，和前面类似
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignUpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferenceManager = new PreferenceManager(getApplicationContext());
        setListeners();
    }

    private void setListeners() {//设置三个监听器，选择头像，点击SignIn，点击SignUp
        binding.textSignIn.setOnClickListener(v -> onBackPressed());//监听点击SignIn
        binding.buttonSignUp.setOnClickListener( v -> {//监听点击SignUp
            if(isValidSignUpDetails()) {//检查是否有效
                signUp();
            }
        });
        binding.layoutImage.setOnClickListener(v -> {
            //创建一个intent用于打开安卓系统的图片选择器
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            //通过addFlag授予读取URI的权限
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            pickImage.launch(intent);//启动图片选择器
        });
    }

    private void showToast(String message){
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    //用户点击Sign Up后触发
    private void signUp(){
        loading(true);//加载进度动画
        FirebaseFirestore database = FirebaseFirestore.getInstance();//实例化一个database
        HashMap<String, Object> user = new HashMap<>();//实例化一个User
        //将用户在文本框输入信息的binding里头读取用户名字，邮箱，密码，字符串形式的头像四个信息赋给user
        user.put(Constants.KEY_NAME, binding.inputName.getText().toString());
        user.put(Constants.KEY_EMAIL, binding.inputEmail.getText().toString());
        user.put(Constants.KEY_PASSWORD, binding.inputPassword.getText().toString());
        user.put(Constants.KEY_IMAGE, encodedImage);
        //然后跟Firebase进行交互，将User添加到Firebase数据库
        database.collection(Constants.KEY_COLLECTION_USERS)//从数据库中提取user列表
                .add(user)//把当前user添加到User列表
                .addOnSuccessListener(documentReference -> {//监听添加成功
                    loading(false);
                    //更新preferenceManager信息，记住用户，下次打开app不用重新输入用户账号密码
                    preferenceManager.putBoolean(Constants.KEY_IS_SIGNED_IN, true);
                    preferenceManager.putString(Constants.KEY_USER_ID, documentReference.getId());
                    preferenceManager.putString(Constants.KEY_NAME, binding.inputName.getText().toString());
                    preferenceManager.putString(Constants.KEY_IMAGE, encodedImage);
                    //用intent来清除当前任务栈，开启新任务，并回到任务栈中的上一个任务
                    Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .addOnFailureListener(exception -> {
                    loading(false);
                    showToast(exception.getMessage());//登录失败的话显示登录失败的错误信息
                });
    }

    private String encodeImage(Bitmap bitmap){
        //设置宽度为150像素
        int previewWidth = 150;
        //按照原来的图片比例设置高度
        int previewHeight = bitmap.getHeight()*previewWidth/bitmap.getWidth();
        //先创建一个图片的预览
        Bitmap previewBitmap = Bitmap.createScaledBitmap(bitmap, previewWidth, previewHeight, false);
        //对图片进行一个压缩，压缩参数：格式JEPG，质量系数50，输出流选择字节输出流
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        previewBitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream);
        byte[] bytes = byteArrayOutputStream.toByteArray();
        //最后用base64工具将字节输出流转化为Base64字符串
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }

    private final ActivityResultLauncher<Intent> pickImage = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if(result.getResultCode() == RESULT_OK){
                    if(result.getData() != null){
                        Uri imageUri = result.getData().getData();
                        try{
                            InputStream inputStream = getContentResolver().openInputStream(imageUri);
                            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                            binding.imageProfile.setImageBitmap(bitmap);
                            binding.textAddImage.setVisibility(View.GONE);
                            encodedImage = encodeImage(bitmap);
                        } catch (FileNotFoundException e){
                            e.printStackTrace();
                        }
                    }
                }
            }
    );

    private Boolean isValidSignUpDetails(){//检查用户有无遗漏输入，输入格式是否正确
        if(encodedImage == null){
            showToast("Select profile image");
            return false;
        } else if (binding.inputName.getText().toString().trim().isEmpty()) {
            showToast("Enter name");
            return false;
        } else if (binding.inputEmail.getText().toString().trim().isEmpty()) {
            showToast("Enter name");
            return false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(binding.inputEmail.getText().toString()).matches()) {
            showToast("Enter valid Email");
            return false;
        } else if (binding.inputPassword.getText().toString().trim().isEmpty()) {
            showToast("Enter password");
            return false;
        } else if (binding.inputConfirmedPassword.getText().toString().trim().isEmpty()) {
            showToast("Confirm your password");
            return false;
        } else if (!binding.inputPassword.getText().toString().equals(binding.inputConfirmedPassword.getText().toString())) {
            showToast("Password & confirmed password must be same!");
            return false;
        } else {
            return true;
        }
    }

    private void loading(Boolean isLoading){//在SignUp按钮和进度条中根据当前是不是处于加载中的状态选择一个来显示
        if(isLoading){
            binding.buttonSignUp.setVisibility(View.INVISIBLE);
            binding.progressBar.setVisibility(View.VISIBLE);
        } else {
            binding.progressBar.setVisibility(View.INVISIBLE);
            binding.buttonSignUp.setVisibility(View.VISIBLE);
        }
    }

}